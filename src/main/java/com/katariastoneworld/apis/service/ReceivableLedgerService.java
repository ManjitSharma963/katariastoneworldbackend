package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.ReceivableBorrowerSummaryDTO;
import com.katariastoneworld.apis.dto.ReceivableLendRequestDTO;
import com.katariastoneworld.apis.dto.ReceivableLedgerEntryResponseDTO;
import com.katariastoneworld.apis.entity.LoanBorrower;
import com.katariastoneworld.apis.entity.ReceivableLedgerEntry;
import com.katariastoneworld.apis.entity.ReceivableLedgerEntryType;
import com.katariastoneworld.apis.repository.LoanBorrowerRepository;
import com.katariastoneworld.apis.repository.ReceivableLedgerEntryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@Transactional
public class ReceivableLedgerService {

    private static final String UNSPECIFIED_KEY = "__unspecified__";
    private static final int LOCATION_MAX = 50;
    private static final int DISPLAY_NAME_MAX = 200;
    private static final int NAME_KEY_MAX = 200;

    @Autowired
    private LoanBorrowerRepository loanBorrowerRepository;

    @Autowired
    private ReceivableLedgerEntryRepository receivableLedgerEntryRepository;

    @Autowired
    private MoneyTransactionService moneyTransactionService;

    /**
     * Record principal lent out (cash/UPI/bank/cheque). Unified ledger: DEBIT.
     * Cash/UPI reduce today's in-hand daily budget.
     */
    public void recordDisbursement(String location, ReceivableLendRequestDTO body) {
        if (location == null || location.isBlank() || body == null || body.getAmount() == null
                || body.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String loc = normalizeLocation(location);
        LoanBorrower borrower = resolveBorrowerForRequest(loc, body);
        ReceivableLedgerEntry entry = new ReceivableLedgerEntry();
        entry.setLocation(loc);
        entry.setBorrowerId(borrower.getId());
        entry.setEntryType(ReceivableLedgerEntryType.DISBURSEMENT);
        entry.setAmount(body.getAmount().setScale(2, RoundingMode.HALF_UP));
        entry.setEntryDate(LocalDate.now());
        String mode = normalizePaymentMode(body.getPaymentMode());
        entry.setNotes(composeNotesWithMode(body.getNotes(), mode));
        receivableLedgerEntryRepository.save(entry);
        moneyTransactionService.recordReceivableDisbursement(entry, borrower, mode);
    }

    /**
     * Record repayment received from a borrower. Unified ledger: CREDIT.
     * Cash/UPI increase today's in-hand daily budget.
     */
    public void recordRepaymentReceived(String location, ReceivableLendRequestDTO body) {
        if (location == null || location.isBlank() || body == null || body.getAmount() == null
                || body.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String loc = normalizeLocation(location);
        LoanBorrower borrower = resolveBorrowerForRequest(loc, body);
        ReceivableLedgerEntry entry = new ReceivableLedgerEntry();
        entry.setLocation(loc);
        entry.setBorrowerId(borrower.getId());
        entry.setEntryType(ReceivableLedgerEntryType.REPAYMENT_RECEIVED);
        entry.setAmount(body.getAmount().setScale(2, RoundingMode.HALF_UP));
        entry.setEntryDate(LocalDate.now());
        String mode = normalizePaymentMode(body.getPaymentMode());
        entry.setNotes(composeNotesWithMode(body.getNotes(), mode));
        receivableLedgerEntryRepository.save(entry);
        moneyTransactionService.recordReceivableRepaymentReceived(entry, borrower, mode);
    }

    public List<ReceivableBorrowerSummaryDTO> listBorrowerSummaries(String location) {
        String loc = normalizeLocation(location);
        if (loc.isEmpty()) {
            return List.of();
        }
        return loanBorrowerRepository.findByLocationNormalizedOrderByDisplayNameAsc(loc).stream()
                .map(b -> {
                    BigDecimal lent = receivableLedgerEntryRepository
                            .sumAmountByLocationBorrowerAndType(loc, b.getId(), ReceivableLedgerEntryType.DISBURSEMENT);
                    BigDecimal collected = receivableLedgerEntryRepository
                            .sumAmountByLocationBorrowerAndType(loc, b.getId(), ReceivableLedgerEntryType.REPAYMENT_RECEIVED);
                    if (lent == null) {
                        lent = BigDecimal.ZERO;
                    }
                    if (collected == null) {
                        collected = BigDecimal.ZERO;
                    }
                    lent = lent.setScale(2, RoundingMode.HALF_UP);
                    collected = collected.setScale(2, RoundingMode.HALF_UP);
                    return new ReceivableBorrowerSummaryDTO(
                            b.getId(),
                            b.getDisplayName(),
                            lent,
                            collected,
                            lent.subtract(collected).setScale(2, RoundingMode.HALF_UP)
                    );
                })
                .collect(Collectors.toList());
    }

    public List<ReceivableLedgerEntryResponseDTO> listLedgerForBorrower(String location, Long borrowerId) {
        String loc = normalizeLocation(location);
        if (loc.isEmpty() || borrowerId == null) {
            return List.of();
        }
        assertBorrowerBelongsToLocation(borrowerId, loc);
        return receivableLedgerEntryRepository
                .findByLocationNormalizedAndBorrowerIdOrderByEntryDateDescIdDesc(loc, borrowerId).stream()
                .map(this::toEntryDto)
                .collect(Collectors.toList());
    }

    private ReceivableLedgerEntryResponseDTO toEntryDto(ReceivableLedgerEntry e) {
        ReceivableLedgerEntryResponseDTO dto = new ReceivableLedgerEntryResponseDTO();
        dto.setId(e.getId());
        dto.setEntryType(e.getEntryType() != null ? e.getEntryType().name() : null);
        dto.setAmount(e.getAmount());
        dto.setEntryDate(e.getEntryDate());
        dto.setNotes(e.getNotes());
        dto.setCreatedAt(e.getCreatedAt());
        return dto;
    }

    private LoanBorrower resolveBorrowerForRequest(String loc, ReceivableLendRequestDTO body) {
        if (body.getBorrowerId() != null) {
            LoanBorrower borrower = loanBorrowerRepository.findById(body.getBorrowerId())
                    .orElseThrow(() -> new IllegalArgumentException("Borrower not found: " + body.getBorrowerId()));
            String bLoc = normalizeLocation(borrower.getLocation());
            if (!loc.equalsIgnoreCase(bLoc)) {
                throw new IllegalArgumentException(
                        "That borrower belongs to another location. Pick a borrower from your list or add a new one.");
            }
            return borrower;
        }
        return findOrCreateBorrower(loc, body.getBorrowerName());
    }

    private LoanBorrower findOrCreateBorrower(String location, String rawName) {
        String key = nameKey(rawName);
        String display = trimToLen((rawName == null || rawName.isBlank()) ? "Unspecified" : rawName.trim(), DISPLAY_NAME_MAX);
        String canonicalLoc = normalizeLocation(location);
        return loanBorrowerRepository.findByLocationNormalizedAndNameKey(canonicalLoc, key)
                .orElseGet(() -> {
                    LoanBorrower b = new LoanBorrower();
                    b.setLocation(canonicalLoc);
                    b.setDisplayName(display);
                    b.setNameKey(key);
                    return loanBorrowerRepository.save(b);
                });
    }

    private static String nameKey(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return UNSPECIFIED_KEY;
        }
        return trimToLen(rawName.trim().toLowerCase(Locale.ROOT), NAME_KEY_MAX);
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
        if ("bank transfer".equals(v) || "bank-transfer".equals(v) || "bank".equals(v)) {
            return "bank_transfer";
        }
        if ("cheque".equals(v) || "check".equals(v)) {
            return "cheque";
        }
        if ("upi".equals(v)) {
            return "upi";
        }
        if ("cash".equals(v)) {
            return "cash";
        }
        return v;
    }

    private static String composeNotesWithMode(String notes, String mode) {
        String n = trimToNull(notes);
        String m = trimToNull(mode);
        if (m == null) {
            return n;
        }
        String suffix = "Mode: " + m;
        if (n == null) {
            return suffix;
        }
        return n + " · " + suffix;
    }

    private void assertBorrowerBelongsToLocation(Long borrowerId, String requestLocation) {
        LoanBorrower borrower = loanBorrowerRepository.findById(borrowerId)
                .orElseThrow(() -> new IllegalArgumentException("Borrower not found: " + borrowerId));
        String reqLoc = normalizeLocation(requestLocation);
        String bLoc = normalizeLocation(borrower.getLocation());
        if (reqLoc.isEmpty() || !reqLoc.equalsIgnoreCase(bLoc)) {
            throw new IllegalArgumentException(
                    "Borrower does not match your login location. Pick the borrower again or record under this location.");
        }
    }

    private static String normalizeLocation(String location) {
        return trimToLen(location != null ? location.trim() : "", LOCATION_MAX);
    }

    private static String trimToLen(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
