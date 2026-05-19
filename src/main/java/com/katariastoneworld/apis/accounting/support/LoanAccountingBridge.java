package com.katariastoneworld.apis.accounting.support;

import com.katariastoneworld.apis.accounting.command.PostMoneyCommand;
import com.katariastoneworld.apis.accounting.config.AccountingFeatureFlags;
import com.katariastoneworld.apis.accounting.domain.AccountingEventType;
import com.katariastoneworld.apis.entity.Expense;
import com.katariastoneworld.apis.entity.LedgerPaymentMode;
import com.katariastoneworld.apis.entity.LedgerSources;
import com.katariastoneworld.apis.entity.LedgerTransactionType;
import com.katariastoneworld.apis.entity.MoneyCategory;
import com.katariastoneworld.apis.entity.MoneyDirection;
import com.katariastoneworld.apis.entity.MoneyPaymentMode;
import com.katariastoneworld.apis.entity.MoneyReferenceType;
import com.katariastoneworld.apis.service.MoneyTransactionLegacySync;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** Market loan received (IN) and loan repayment outflow (OUT) keyed by loan/expense ids. */
@Component
public class LoanAccountingBridge {

    private static final String MODULE = "MARKET_LOAN";

    private final AccountingFeatureFlags flags;
    private final AccountingPostingSupport posting;
    private final MoneyTransactionLegacySync legacySync;

    public LoanAccountingBridge(
            AccountingFeatureFlags flags,
            AccountingPostingSupport posting,
            MoneyTransactionLegacySync legacySync) {
        this.flags = flags;
        this.posting = posting;
        this.legacySync = legacySync;
    }

    public void postLoanReceived(
            String location,
            Long loanEntryId,
            Long lenderId,
            String lenderName,
            BigDecimal amount,
            LedgerPaymentMode paymentMode,
            LocalDate entryDate,
            String notes) {
        if (location == null || loanEntryId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        if (flags.isLoanEnabled()) {
            PostMoneyCommand command = PostMoneyCommand.builder()
                    .location(location.trim())
                    .transactionDate(entryDate != null ? entryDate : LocalDate.now())
                    .amount(amt)
                    .direction(MoneyDirection.IN)
                    .category(MoneyCategory.LOAN)
                    .subCategory("LOAN_TAKEN")
                    .referenceType(MoneyReferenceType.loan)
                    .referenceId(loanEntryId)
                    .paymentMode(mapLedgerMode(paymentMode))
                    .partyId(lenderId)
                    .partyName(lenderName)
                    .requestId("LOAN:RECEIVED:" + loanEntryId)
                    .ledgerTxnType(LedgerSources.LOAN)
                    .notes(notes)
                    .eventType(AccountingEventType.LOAN_DISBURSE)
                    .metadataJson(AccountingPostingSupport.balancedEntryMetadata("CASH", "MARKET_LOAN_LIABILITY"))
                    .build();
            posting.postIn(command, MODULE);
            return;
        }
        legacySync.syncFromUnified(
                location.trim(),
                entryDate != null ? entryDate : LocalDate.now(),
                amt,
                LedgerTransactionType.CREDIT,
                paymentMode != null ? paymentMode : LedgerPaymentMode.CASH,
                LedgerSources.LOAN,
                loanEntryId,
                notes);
    }

    public void postLoanRepayment(Expense expense, String notes) {
        if (expense == null || expense.getId() == null || expense.getLocation() == null) {
            return;
        }
        if (expense.getAmount() == null || expense.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            voidLoanRepayment(expense.getLocation(), expense.getId(), "loan repayment cleared");
            return;
        }
        BigDecimal amt = expense.getAmount().setScale(2, RoundingMode.HALF_UP);
        LedgerPaymentMode mode = LedgerPaymentMode.fromLegacyPaymentMethod(expense.getPaymentMethod());
        if (flags.isLoanEnabled()) {
            PostMoneyCommand command = PostMoneyCommand.builder()
                    .location(expense.getLocation().trim())
                    .transactionDate(expense.getDate() != null ? expense.getDate() : LocalDate.now())
                    .amount(amt)
                    .direction(MoneyDirection.OUT)
                    .category(MoneyCategory.LOAN)
                    .subCategory("LOAN_REPAYMENT_OUT")
                    .referenceType(MoneyReferenceType.loan)
                    .referenceId(expense.getId())
                    .paymentMode(mapLedgerMode(mode))
                    .requestId("LOAN:REPAY:" + expense.getId())
                    .ledgerTxnType(LedgerSources.LOAN_REPAY)
                    .notes(notes != null ? notes : expense.getDescription())
                    .eventType(AccountingEventType.LOAN_REPAY)
                    .metadataJson(AccountingPostingSupport.balancedEntryMetadata("MARKET_LOAN_LIABILITY", "CASH"))
                    .build();
            posting.postOut(command, MODULE);
            return;
        }
        legacySync.syncFromUnified(
                expense.getLocation().trim(),
                expense.getDate() != null ? expense.getDate() : LocalDate.now(),
                amt,
                LedgerTransactionType.DEBIT,
                mode,
                LedgerSources.LOAN_REPAY,
                expense.getId(),
                notes != null ? notes : expense.getDescription());
    }

    public void voidLoanRepayment(String location, Long expenseId, String reason) {
        if (expenseId == null) {
            return;
        }
        if (!flags.isLoanEnabled()) {
            legacySync.hardDeleteSyncedLine(LedgerSources.LOAN_REPAY, expenseId);
            return;
        }
        posting.voidByRequestId(location, "LOAN:REPAY:" + expenseId, reason, MODULE);
        posting.voidByReference(
                location, MoneyReferenceType.loan, expenseId, MoneyCategory.LOAN, LedgerSources.LOAN_REPAY, reason, MODULE);
    }

    public void voidLoanReceived(String location, Long loanEntryId, String reason) {
        if (loanEntryId == null) {
            return;
        }
        if (!flags.isLoanEnabled()) {
            legacySync.hardDeleteSyncedLine(LedgerSources.LOAN, loanEntryId);
            return;
        }
        posting.voidByRequestId(location, "LOAN:RECEIVED:" + loanEntryId, reason, MODULE);
        posting.voidByReference(
                location, MoneyReferenceType.loan, loanEntryId, MoneyCategory.LOAN, LedgerSources.LOAN, reason, MODULE);
    }

    public boolean tryVoidByLedgerSource(String location, String source, Long referenceId, String reason) {
        if (!flags.isLoanEnabled() || referenceId == null || source == null) {
            return false;
        }
        String s = source.trim().toUpperCase();
        if (LedgerSources.LOAN_REPAY.equals(s)) {
            voidLoanRepayment(location, referenceId, reason);
            return true;
        }
        if (LedgerSources.LOAN.equals(s)) {
            voidLoanReceived(location, referenceId, reason);
            return true;
        }
        return false;
    }

    private static MoneyPaymentMode mapLedgerMode(LedgerPaymentMode mode) {
        if (mode == null) {
            return MoneyPaymentMode.CASH;
        }
        return switch (mode) {
            case CASH -> MoneyPaymentMode.CASH;
            case UPI -> MoneyPaymentMode.UPI;
            case BANK, CARD, CHEQUE -> MoneyPaymentMode.BANK;
        };
    }
}
