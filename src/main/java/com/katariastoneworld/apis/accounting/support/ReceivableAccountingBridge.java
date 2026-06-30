package com.katariastoneworld.apis.accounting.support;

import com.katariastoneworld.apis.accounting.command.PostMoneyCommand;
import com.katariastoneworld.apis.accounting.config.AccountingFeatureFlags;
import com.katariastoneworld.apis.accounting.domain.AccountingEventType;
import com.katariastoneworld.apis.entity.LoanBorrower;
import com.katariastoneworld.apis.entity.MoneyCategory;
import com.katariastoneworld.apis.entity.MoneyDirection;
import com.katariastoneworld.apis.entity.MoneyPaymentMode;
import com.katariastoneworld.apis.entity.MoneyReferenceType;
import com.katariastoneworld.apis.entity.MoneyTxnStatus;
import com.katariastoneworld.apis.entity.ReceivableLedgerEntry;
import com.katariastoneworld.apis.repository.MoneyTransactionRepository;
import com.katariastoneworld.apis.service.MoneyTransactionLegacySync;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;

/** Loan given to borrower (OUT) and repayment received (IN). */
@Component
public class ReceivableAccountingBridge {

    private static final String MODULE = "RECEIVABLE";

    private final AccountingFeatureFlags flags;
    private final AccountingPostingSupport posting;
    private final MoneyTransactionRepository moneyTransactionRepository;
    private final MoneyTransactionLegacySync legacySync;

    public ReceivableAccountingBridge(
            AccountingFeatureFlags flags,
            AccountingPostingSupport posting,
            MoneyTransactionRepository moneyTransactionRepository,
            MoneyTransactionLegacySync legacySync) {
        this.flags = flags;
        this.posting = posting;
        this.moneyTransactionRepository = moneyTransactionRepository;
        this.legacySync = legacySync;
    }

    public void postDisbursement(ReceivableLedgerEntry entry, LoanBorrower borrower, String normalizedPaymentMode) {
        if (entry == null || entry.getId() == null || borrower == null || entry.getAmount() == null
                || entry.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return;
        }
        if (flags.isReceivableEnabled()) {
            if (moneyTransactionRepository.findFirstByReferenceTypeAndReferenceIdAndTxnTypeAndStatusAndIsDeletedFalseOrderByIdAsc(
                    MoneyReferenceType.loan, entry.getId(), "LOAN_GIVEN", MoneyTxnStatus.ACTIVE).isPresent()) {
                return;
            }
            PostMoneyCommand command = PostMoneyCommand.builder()
                    .location(entry.getLocation())
                    .transactionDate(entry.getEntryDate())
                    .amount(entry.getAmount().setScale(2, RoundingMode.HALF_UP))
                    .direction(MoneyDirection.OUT)
                    .category(MoneyCategory.LOAN)
                    .subCategory("LOAN_GIVEN")
                    .referenceType(MoneyReferenceType.loan)
                    .referenceId(entry.getId())
                    .paymentMode(mapMode(normalizedPaymentMode))
                    .partyId(borrower.getId())
                    .partyName(resolveBorrowerName(borrower))
                    .requestId("RECEIVABLE:DISBURSE:" + entry.getId())
                    .ledgerTxnType("LOAN_GIVEN")
                    .notes(buildNotes("Loan given", borrower, entry))
                    .eventType(AccountingEventType.GENERIC_OUT)
                    .metadataJson(AccountingPostingSupport.balancedEntryMetadata("RECEIVABLE_ASSET", "CASH"))
                    .build();
            posting.postOut(command, MODULE);
            return;
        }
        legacySync.recordReceivableDisbursement(entry, borrower, normalizedPaymentMode);
    }

    public void postRepaymentReceived(ReceivableLedgerEntry entry, LoanBorrower borrower, String normalizedPaymentMode) {
        if (entry == null || entry.getId() == null || borrower == null || entry.getAmount() == null
                || entry.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return;
        }
        if (flags.isReceivableEnabled()) {
            if (moneyTransactionRepository.findFirstByReferenceTypeAndReferenceIdAndTxnTypeAndStatusAndIsDeletedFalseOrderByIdAsc(
                    MoneyReferenceType.loan, entry.getId(), "LOAN_GIVEN_REPAY", MoneyTxnStatus.ACTIVE).isPresent()) {
                return;
            }
            PostMoneyCommand command = PostMoneyCommand.builder()
                    .location(entry.getLocation())
                    .transactionDate(entry.getEntryDate())
                    .amount(entry.getAmount().setScale(2, RoundingMode.HALF_UP))
                    .direction(MoneyDirection.IN)
                    .category(MoneyCategory.LOAN)
                    .subCategory("LOAN_GIVEN_REPAY")
                    .referenceType(MoneyReferenceType.loan)
                    .referenceId(entry.getId())
                    .paymentMode(mapMode(normalizedPaymentMode))
                    .partyId(borrower.getId())
                    .partyName(resolveBorrowerName(borrower))
                    .requestId("RECEIVABLE:REPAY:" + entry.getId())
                    .ledgerTxnType("LOAN_GIVEN_REPAY")
                    .notes(buildNotes("Loan repayment received", borrower, entry))
                    .eventType(AccountingEventType.GENERIC_IN)
                    .metadataJson(AccountingPostingSupport.balancedEntryMetadata("CASH", "RECEIVABLE_ASSET"))
                    .build();
            posting.postIn(command, MODULE);
            return;
        }
        legacySync.recordReceivableRepaymentReceived(entry, borrower, normalizedPaymentMode);
    }

    public boolean tryVoidByLedgerSource(String location, String source, Long referenceId, String reason) {
        if (!flags.isReceivableEnabled() || referenceId == null || source == null) {
            return false;
        }
        String s = source.trim().toUpperCase();
        if ("LOAN_GIVEN".equals(s) || "LOAN_GIVEN_REPAY".equals(s)) {
            voidReceivableEntry(location, referenceId, s, reason);
            return true;
        }
        return false;
    }

    public void voidReceivableEntry(String location, Long receivableEntryId, String ledgerTxnType, String reason) {
        if (receivableEntryId == null) {
            return;
        }
        if (flags.isReceivableEnabled()) {
            if ("LOAN_GIVEN".equals(ledgerTxnType)) {
                posting.voidByRequestId(location, "RECEIVABLE:DISBURSE:" + receivableEntryId, reason, MODULE);
            } else if ("LOAN_GIVEN_REPAY".equals(ledgerTxnType)) {
                posting.voidByRequestId(location, "RECEIVABLE:REPAY:" + receivableEntryId, reason, MODULE);
            }
            posting.voidByReference(
                    location, MoneyReferenceType.loan, receivableEntryId, MoneyCategory.LOAN, ledgerTxnType, reason, MODULE);
            return;
        }
        legacySync.hardDeleteSyncedLine(ledgerTxnType, receivableEntryId);
    }

    private static String resolveBorrowerName(LoanBorrower borrower) {
        if (borrower.getDisplayName() != null && !borrower.getDisplayName().isBlank()) {
            return borrower.getDisplayName().trim();
        }
        return borrower.getId() != null ? ("Borrower_" + borrower.getId()) : "Borrower_Unknown";
    }

    private static String buildNotes(String prefix, LoanBorrower borrower, ReceivableLedgerEntry entry) {
        String base = prefix + " · borrower=" + resolveBorrowerName(borrower);
        if (entry.getNotes() != null && !entry.getNotes().isBlank()) {
            return base + " · " + entry.getNotes();
        }
        return base;
    }

    private static MoneyPaymentMode mapMode(String normalizedPaymentMode) {
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
}
