package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.LoanLedgerEntryResponseDTO;
import com.katariastoneworld.apis.dto.LoanLenderSummaryDTO;
import com.katariastoneworld.apis.dto.LoanReceiptRequestDTO;
import com.katariastoneworld.apis.entity.Expense;
import com.katariastoneworld.apis.entity.ExpenseCategory;
import com.katariastoneworld.apis.entity.LoanLedgerEntry;
import com.katariastoneworld.apis.entity.LoanLedgerEntryType;
import com.katariastoneworld.apis.entity.LoanLender;
import com.katariastoneworld.apis.repository.LoanLedgerEntryRepository;
import com.katariastoneworld.apis.repository.LoanLenderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class LoanLedgerService {

    private static final String UNSPECIFIED_KEY = "__unspecified__";

    @Autowired
    private LoanLenderRepository loanLenderRepository;

    @Autowired
    private LoanLedgerEntryRepository loanLedgerEntryRepository;

    @Autowired
    private DailyBudgetService dailyBudgetService;

    public void recordLoanReceipt(String location, LoanReceiptRequestDTO body) {
        if (location == null || location.isBlank() || body == null || body.getAmount() == null
                || body.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String loc = location.trim();
        LoanLender lender = resolveLenderForReceipt(loc, body);
        LoanLedgerEntry entry = new LoanLedgerEntry();
        entry.setLocation(loc);
        entry.setLenderId(lender.getId());
        entry.setEntryType(LoanLedgerEntryType.RECEIPT);
        entry.setAmount(body.getAmount().setScale(2, RoundingMode.HALF_UP));
        entry.setEntryDate(LocalDate.now());
        String mode = normalizePaymentMode(body.getPaymentMode());
        entry.setNotes(composeNotesWithMode(body.getNotes(), mode));
        loanLedgerEntryRepository.save(entry);

        if (affectsDailyBudget(mode)) {
            dailyBudgetService.recordLoanReceipt(
                    loc,
                    body.getAmount(),
                    lender.getDisplayName(),
                    composeNotesWithMode(body.getNotes(), mode));
        }
    }

    public void recordRepayment(Expense expense, Long lenderId) {
        if (expense == null || lenderId == null || expense.getAmount() == null) {
            return;
        }
        String loc = expense.getLocation();
        assertLenderBelongsToLocation(lenderId, loc);

        LoanLedgerEntry entry = new LoanLedgerEntry();
        entry.setLocation(loc.trim());
        entry.setLenderId(lenderId);
        entry.setEntryType(LoanLedgerEntryType.REPAYMENT);
        entry.setAmount(expense.getAmount().setScale(2, RoundingMode.HALF_UP));
        entry.setEntryDate(expense.getDate() != null ? expense.getDate() : LocalDate.now());
        entry.setNotes(composeNotesWithMode(expense.getDescription(), normalizePaymentMode(expense.getPaymentMethod())));
        entry.setExpenseId(expense.getId());
        loanLedgerEntryRepository.save(entry);
    }

    /**
     * Keeps loan_ledger_entries in sync when a daily expense is created/updated as loan repayment.
     */
    public void syncRepaymentLedger(Expense expense, Long lenderIdFromRequest) {
        if (expense == null || expense.getId() == null) {
            return;
        }
        Optional<LoanLedgerEntry> opt = loanLedgerEntryRepository.findByExpenseId(expense.getId());
        boolean isLoanRepayment = expense.getExpenseCategory() == ExpenseCategory.LOAN
                && expense.getType() != null && "daily".equalsIgnoreCase(expense.getType().trim());

        if (!isLoanRepayment) {
            opt.ifPresent(loanLedgerEntryRepository::delete);
            return;
        }
        if (lenderIdFromRequest == null) {
            opt.ifPresent(loanLedgerEntryRepository::delete);
            return;
        }
        assertLenderBelongsToLocation(lenderIdFromRequest, expense.getLocation());

        if (opt.isPresent()) {
            LoanLedgerEntry le = opt.get();
            le.setLenderId(lenderIdFromRequest);
            le.setAmount(expense.getAmount() != null
                    ? expense.getAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            le.setEntryDate(expense.getDate() != null ? expense.getDate() : LocalDate.now());
            le.setNotes(composeNotesWithMode(expense.getDescription(), normalizePaymentMode(expense.getPaymentMethod())));
            loanLedgerEntryRepository.save(le);
        } else {
            recordRepayment(expense, lenderIdFromRequest);
        }
    }

    public void deleteRepaymentByExpenseId(Long expenseId) {
        if (expenseId == null) {
            return;
        }
        loanLedgerEntryRepository.findByExpenseId(expenseId).ifPresent(loanLedgerEntryRepository::delete);
    }

    public Optional<Long> findLenderIdForExpense(Long expenseId) {
        if (expenseId == null) {
            return Optional.empty();
        }
        return loanLedgerEntryRepository.findByExpenseId(expenseId).map(LoanLedgerEntry::getLenderId);
    }

    public List<LoanLenderSummaryDTO> listLenderSummaries(String location) {
        String loc = location == null ? "" : location.trim();
        if (loc.isEmpty()) {
            return List.of();
        }
        return loanLenderRepository.findByLocationNormalizedOrderByDisplayNameAsc(loc).stream()
                .map(l -> {
                    BigDecimal borrowed = loanLedgerEntryRepository
                            .sumAmountByLocationLenderAndType(loc, l.getId(), LoanLedgerEntryType.RECEIPT);
                    BigDecimal repaid = loanLedgerEntryRepository
                            .sumAmountByLocationLenderAndType(loc, l.getId(), LoanLedgerEntryType.REPAYMENT);
                    if (borrowed == null) {
                        borrowed = BigDecimal.ZERO;
                    }
                    if (repaid == null) {
                        repaid = BigDecimal.ZERO;
                    }
                    borrowed = borrowed.setScale(2, RoundingMode.HALF_UP);
                    repaid = repaid.setScale(2, RoundingMode.HALF_UP);
                    return new LoanLenderSummaryDTO(
                            l.getId(),
                            l.getDisplayName(),
                            borrowed,
                            repaid,
                            borrowed.subtract(repaid).setScale(2, RoundingMode.HALF_UP)
                    );
                })
                .collect(Collectors.toList());
    }

    public List<LoanLedgerEntryResponseDTO> listLedgerForLender(String location, Long lenderId) {
        String loc = location == null ? "" : location.trim();
        if (loc.isEmpty() || lenderId == null) {
            return List.of();
        }
        assertLenderBelongsToLocation(lenderId, loc);
        return loanLedgerEntryRepository.findByLocationNormalizedAndLenderIdOrderByEntryDateDescIdDesc(loc, lenderId).stream()
                .map(this::toEntryDto)
                .collect(Collectors.toList());
    }

    private LoanLedgerEntryResponseDTO toEntryDto(LoanLedgerEntry e) {
        LoanLedgerEntryResponseDTO dto = new LoanLedgerEntryResponseDTO();
        dto.setId(e.getId());
        dto.setEntryType(e.getEntryType() != null ? e.getEntryType().name() : null);
        dto.setAmount(e.getAmount());
        dto.setEntryDate(e.getEntryDate());
        dto.setNotes(e.getNotes());
        dto.setExpenseId(e.getExpenseId());
        dto.setCreatedAt(e.getCreatedAt());
        return dto;
    }

    private LoanLender resolveLenderForReceipt(String loc, LoanReceiptRequestDTO body) {
        if (body.getLenderId() != null) {
            LoanLender lender = loanLenderRepository.findById(body.getLenderId())
                    .orElseThrow(() -> new IllegalArgumentException("Lender not found: " + body.getLenderId()));
            String lenLoc = lender.getLocation() != null ? lender.getLocation().trim() : "";
            if (!loc.equalsIgnoreCase(lenLoc)) {
                throw new IllegalArgumentException(
                        "That lender belongs to another location. Pick a lender from your list or add a new one.");
            }
            return lender;
        }
        return findOrCreateLender(loc, body.getLenderName());
    }

    private LoanLender findOrCreateLender(String location, String rawName) {
        String key = nameKey(rawName);
        String display = (rawName == null || rawName.isBlank()) ? "Unspecified" : rawName.trim();
        String canonicalLoc = location != null ? location.trim() : "";
        return loanLenderRepository.findByLocationNormalizedAndNameKey(canonicalLoc, key)
                .orElseGet(() -> {
                    LoanLender l = new LoanLender();
                    l.setLocation(canonicalLoc);
                    l.setDisplayName(display);
                    l.setNameKey(key);
                    return loanLenderRepository.save(l);
                });
    }

    private static String nameKey(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return UNSPECIFIED_KEY;
        }
        return rawName.trim().toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String normalizePaymentMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return "cash";
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if ("bank transfer".equals(v) || "bank-transfer".equals(v) || "bank".equals(v)) return "bank_transfer";
        if ("cheque".equals(v) || "check".equals(v)) return "cheque";
        if ("upi".equals(v)) return "upi";
        if ("cash".equals(v)) return "cash";
        // Accept unknown string but treat it as non-budget affecting to be safe.
        return v;
    }

    private static boolean affectsDailyBudget(String paymentMode) {
        if (paymentMode == null) return true;
        String v = paymentMode.trim().toLowerCase(Locale.ROOT);
        return "cash".equals(v) || "upi".equals(v);
    }

    private static String composeNotesWithMode(String notes, String mode) {
        String n = trimToNull(notes);
        String m = trimToNull(mode);
        if (m == null) return n;
        String suffix = "Mode: " + m;
        if (n == null) return suffix;
        // Keep existing notes readable
        return n + " · " + suffix;
    }

    private void assertLenderBelongsToLocation(Long lenderId, String expenseLocation) {
        LoanLender lender = loanLenderRepository.findById(lenderId)
                .orElseThrow(() -> new IllegalArgumentException("Lender not found: " + lenderId));
        String expLoc = expenseLocation != null ? expenseLocation.trim() : "";
        String lenLoc = lender.getLocation() != null ? lender.getLocation().trim() : "";
        if (expLoc.isEmpty() || !expLoc.equalsIgnoreCase(lenLoc)) {
            throw new IllegalArgumentException(
                    "Lender does not match your login location. Re-open the form and pick the lender again, or record the loan under this location.");
        }
    }
}
