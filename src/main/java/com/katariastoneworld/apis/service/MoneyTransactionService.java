package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.entity.*;
import com.katariastoneworld.apis.repository.MoneyTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Writes to {@code transactions} for reporting / balance APIs that read this table.
 */
@Service
@Transactional
public class MoneyTransactionService {

    private static final int NOTES_MAX = 2000;

    @Autowired
    private MoneyTransactionRepository moneyTransactionRepository;

    /**
     * Generic upsert into {@code transactions} from domain {@code source} strings.
     * Keeps one row per (referenceType, referenceId, category) when referenceId is present.
     */
    public void syncFromUnified(
            String location,
            LocalDate txnDate,
            BigDecimal amount,
            LedgerTransactionType txnType,
            LedgerPaymentMode paymentMode,
            String source,
            Long referenceId,
            String description) {
        if (location == null || location.isBlank() || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0
                || txnType == null || paymentMode == null || source == null || source.isBlank()) {
            return;
        }
        // Bill payments are written with bill.id reference and party_name in BillService#createTransactionFromBillPayment.
        // Skip generic source=BILL rows here to avoid duplicate / mismatched reference_id.
        if ("BILL".equalsIgnoreCase(source.trim())) {
            return;
        }

        MoneyCategory category = categoryFromSource(source);
        MoneyReferenceType referenceType = referenceTypeFromSource(source);
        if (category == null || referenceType == null) {
            return;
        }

        MoneyTransaction row = null;
        if (referenceId != null) {
            row = moneyTransactionRepository
                    .findByReferenceIdAndReferenceTypeAndCategory(referenceId, referenceType, category)
                    .orElse(null);
        }
        if (row == null) {
            row = new MoneyTransaction();
        }
        LocalDate d = txnDate != null ? txnDate : LocalDate.now();
        row.setDateTime(d.atStartOfDay());
        row.setTransactionDate(d);
        row.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        row.setDirection(txnType == LedgerTransactionType.CREDIT ? MoneyDirection.IN : MoneyDirection.OUT);
        row.setCategory(category);
        row.setSubCategory(subCategoryFromSource(source));
        row.setPaymentMode(toMoneyPaymentMode(paymentMode));
        row.setLocation(trimLocation(location));
        row.setOwnerUserId(null);
        row.setReferenceType(referenceType);
        row.setReferenceId(referenceId);
        row.setStatus(MoneyTxnStatus.ACTIVE);
        row.setNotes(notesMax(description));
        row.setIsDeleted(false);
        moneyTransactionRepository.save(row);
    }

    /** Cash/UPI/bank/cheque out: money lent to borrower (receivable_ledger DISBURSEMENT). */
    public void recordReceivableDisbursement(ReceivableLedgerEntry entry, LoanBorrower borrower, String normalizedPaymentMode) {
        if (entry == null || entry.getId() == null || borrower == null || entry.getAmount() == null
                || entry.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (moneyTransactionRepository.existsByReferenceIdAndReferenceTypeAndCategory(
                entry.getId(), MoneyReferenceType.loan, MoneyCategory.LOAN)) {
            return;
        }
        MoneyTransaction row = baseRow(entry, borrower, normalizedPaymentMode);
        row.setDirection(MoneyDirection.OUT);
        row.setCategory(MoneyCategory.LOAN);
        row.setSubCategory("LOAN_GIVEN");
        row.setPartyName(resolveBorrowerPartyName(borrower));
        row.setNotes(notesMax("Loan given · borrower=" + borrower.getDisplayName()
                + (entry.getNotes() != null && !entry.getNotes().isBlank() ? " · " + entry.getNotes() : "")));
        moneyTransactionRepository.save(row);
    }

    /** Cash/UPI/bank/cheque in: repayment received from borrower (receivable_ledger REPAYMENT_RECEIVED). */
    public void recordReceivableRepaymentReceived(ReceivableLedgerEntry entry, LoanBorrower borrower, String normalizedPaymentMode) {
        if (entry == null || entry.getId() == null || borrower == null || entry.getAmount() == null
                || entry.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (moneyTransactionRepository.existsByReferenceIdAndReferenceTypeAndCategory(
                entry.getId(), MoneyReferenceType.loan, MoneyCategory.LOAN)) {
            return;
        }
        MoneyTransaction row = baseRow(entry, borrower, normalizedPaymentMode);
        row.setDirection(MoneyDirection.IN);
        row.setCategory(MoneyCategory.LOAN);
        row.setSubCategory("LOAN_GIVEN_REPAY");
        row.setPartyName(resolveBorrowerPartyName(borrower));
        row.setNotes(notesMax("Loan repayment received · borrower=" + borrower.getDisplayName()
                + (entry.getNotes() != null && !entry.getNotes().isBlank() ? " · " + entry.getNotes() : "")));
        moneyTransactionRepository.save(row);
    }

    private MoneyTransaction baseRow(ReceivableLedgerEntry entry, LoanBorrower borrower, String normalizedPaymentMode) {
        MoneyTransaction row = new MoneyTransaction();
        LocalDateTime when = entry.getCreatedAt() != null ? entry.getCreatedAt() : LocalDateTime.now();
        row.setDateTime(when);
        row.setAmount(entry.getAmount().setScale(2, RoundingMode.HALF_UP));
        row.setPaymentMode(toMoneyPaymentMode(normalizedPaymentMode));
        row.setLocation(trimLocation(borrower.getLocation()));
        row.setOwnerUserId(null);
        row.setPartyId(borrower.getId());
        row.setReferenceId(entry.getId());
        row.setReferenceType(MoneyReferenceType.loan);
        row.setStatus(MoneyTxnStatus.ACTIVE);
        return row;
    }

    private static MoneyPaymentMode toMoneyPaymentMode(String normalizedPaymentMode) {
        if (normalizedPaymentMode == null || normalizedPaymentMode.isBlank()) {
            return MoneyPaymentMode.CASH;
        }
        String v = normalizedPaymentMode.trim().toLowerCase();
        if ("upi".equals(v)) {
            return MoneyPaymentMode.UPI;
        }
        if ("bank_transfer".equals(v) || "cheque".equals(v) || "bank".equals(v)) {
            return MoneyPaymentMode.BANK;
        }
        return MoneyPaymentMode.CASH;
    }

    private static MoneyPaymentMode toMoneyPaymentMode(LedgerPaymentMode mode) {
        if (mode == null) {
            return MoneyPaymentMode.CASH;
        }
        return switch (mode) {
            case CASH -> MoneyPaymentMode.CASH;
            case UPI -> MoneyPaymentMode.UPI;
            case BANK, CARD, CHEQUE -> MoneyPaymentMode.BANK;
        };
    }

    private static MoneyCategory categoryFromSource(String source) {
        String s = source.trim().toUpperCase(Locale.ROOT);
        return switch (s) {
            case "BILL" -> MoneyCategory.BILL;
            case "ADVANCE", "ADVANCE_REFUND" -> MoneyCategory.ADVANCE;
            case "EXPENSE" -> MoneyCategory.EXPENSE;
            case "SALARY_ADVANCE", "SALARY_PAY" -> MoneyCategory.SALARY;
            case "LOAN", "LOAN_REPAY", "LOAN_GIVEN_REPAY", "LOAN_GIVEN" -> MoneyCategory.LOAN;
            case "CLIENT_PAYMENT", "CLIENT_OUT", "CLIENT" -> MoneyCategory.CLIENT_PAYMENT;
            case "BUDGET_ADJUSTMENT" -> MoneyCategory.OTHER;
            default -> MoneyCategory.OTHER;
        };
    }

    private static String subCategoryFromSource(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        String s = source.trim().toUpperCase(Locale.ROOT);
        return switch (s) {
            case "LOAN" -> "LOAN_TAKEN";
            case "LOAN_REPAY" -> "LOAN_REPAYMENT_OUT";
            case "LOAN_GIVEN" -> "LOAN_GIVEN";
            case "LOAN_GIVEN_REPAY" -> "LOAN_COLLECTION";
            case "SALARY_ADVANCE" -> "SALARY_ADVANCE";
            case "SALARY_PAY" -> "SALARY_PAY";
            case "ADVANCE_REFUND" -> "ADVANCE_REFUND";
            case "BUDGET_ADJUSTMENT" -> "BUDGET_ADJUSTMENT";
            default -> s;
        };
    }

    private static MoneyReferenceType referenceTypeFromSource(String source) {
        String s = source.trim().toUpperCase(Locale.ROOT);
        return switch (s) {
            case "BILL", "ADVANCE" -> MoneyReferenceType.bill;
            case "ADVANCE_REFUND" -> MoneyReferenceType.other;
            case "EXPENSE" -> MoneyReferenceType.expense;
            case "SALARY_ADVANCE", "SALARY_PAY" -> MoneyReferenceType.salary;
            case "LOAN", "LOAN_REPAY", "LOAN_GIVEN", "LOAN_GIVEN_REPAY" -> MoneyReferenceType.loan;
            case "CLIENT_PAYMENT", "CLIENT_OUT", "CLIENT" -> MoneyReferenceType.other;
            case "BUDGET_ADJUSTMENT" -> MoneyReferenceType.other;
            default -> MoneyReferenceType.other;
        };
    }

    /**
     * Deletes the {@code transactions} row keyed like {@link #syncFromUnified}, if present.
     * Bill payments are keyed by bill id in {@code transactions} (not payment id) — use {@code BillService} flows instead.
     */
    public void removeSyncedLine(String location, String source, Long referenceId) {
        if (location == null || location.isBlank() || source == null || source.isBlank() || referenceId == null) {
            return;
        }
        if ("BILL".equalsIgnoreCase(source.trim())) {
            return;
        }
        MoneyCategory category = categoryFromSource(source);
        MoneyReferenceType referenceType = referenceTypeFromSource(source);
        moneyTransactionRepository
                .findByReferenceIdAndReferenceTypeAndCategory(referenceId, referenceType, category)
                .ifPresent(moneyTransactionRepository::delete);
    }

    /** Legacy {@code financial_ledger} EXPENSE_DEBIT rows keyed by expense id. */
    public void removeLegacyExpenseDebitLine(Long expenseId) {
        if (expenseId == null) {
            return;
        }
        moneyTransactionRepository
                .findByReferenceIdAndReferenceTypeAndCategory(expenseId, MoneyReferenceType.expense, MoneyCategory.EXPENSE)
                .ifPresent(moneyTransactionRepository::delete);
    }

    private static String trimLocation(String location) {
        if (location == null) {
            return "";
        }
        String t = location.trim();
        return t.length() > 64 ? t.substring(0, 64) : t;
    }

    private static String trimPartyName(String partyName) {
        if (partyName == null) {
            return null;
        }
        String t = partyName.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > 150 ? t.substring(0, 150) : t;
    }

    private static String resolveBorrowerPartyName(LoanBorrower borrower) {
        String byDisplay = trimPartyName(borrower != null ? borrower.getDisplayName() : null);
        if (byDisplay != null && !byDisplay.isBlank()) {
            return byDisplay;
        }
        if (borrower != null && borrower.getId() != null) {
            return "Borrower_" + borrower.getId();
        }
        return "Borrower_Unknown";
    }

    private static String notesMax(String s) {
        if (s == null) {
            return null;
        }
        if (s.length() <= NOTES_MAX) {
            return s;
        }
        return s.substring(0, NOTES_MAX - 3) + "...";
    }
}
