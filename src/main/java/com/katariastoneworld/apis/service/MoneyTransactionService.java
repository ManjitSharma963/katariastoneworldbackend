package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.accounting.api.AccountingTransactionService;
import com.katariastoneworld.apis.accounting.command.VoidByReferenceCommand;
import com.katariastoneworld.apis.accounting.config.AccountingFeatureFlags;
import com.katariastoneworld.apis.accounting.dto.AccountingReference;
import com.katariastoneworld.apis.accounting.support.RefundAccountingBridge;
import com.katariastoneworld.apis.accounting.support.UnifiedLedgerVoidRouter;
import com.katariastoneworld.apis.entity.*;
import com.katariastoneworld.apis.repository.MoneyTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Orchestrates {@code transactions} writes, engine void routing, and legacy sync delegation.
 */
@Service
@Transactional
public class MoneyTransactionService {

    @Autowired
    private MoneyTransactionRepository moneyTransactionRepository;

    @Autowired
    private MoneyTransactionLegacySync legacySync;

    @Autowired
    private AccountingFeatureFlags accountingFeatureFlags;

    @Lazy
    @Autowired
    private RefundAccountingBridge refundAccountingBridge;

    @Autowired
    private AccountingTransactionService accountingTransactionService;

    @Lazy
    @Autowired
    private UnifiedLedgerVoidRouter unifiedLedgerVoidRouter;

    /** @see MoneyTransactionLegacySync#syncFromUnified */
    public void syncFromUnified(
            String location,
            LocalDate txnDate,
            BigDecimal amount,
            LedgerTransactionType txnType,
            LedgerPaymentMode paymentMode,
            String source,
            Long referenceId,
            String description) {
        legacySync.syncFromUnified(location, txnDate, amount, txnType, paymentMode, source, referenceId, description);
    }

    public void recordReceivableDisbursement(ReceivableLedgerEntry entry, LoanBorrower borrower, String normalizedPaymentMode) {
        legacySync.recordReceivableDisbursement(entry, borrower, normalizedPaymentMode);
    }

    public void recordReceivableRepaymentReceived(ReceivableLedgerEntry entry, LoanBorrower borrower, String normalizedPaymentMode) {
        legacySync.recordReceivableRepaymentReceived(entry, borrower, normalizedPaymentMode);
    }

    public void removeSyncedLine(String location, String source, Long referenceId) {
        if (location == null || location.isBlank() || source == null || source.isBlank() || referenceId == null) {
            return;
        }
        if ("BILL".equalsIgnoreCase(source.trim())) {
            return;
        }
        if (unifiedLedgerVoidRouter.tryVoid(location.trim(), source, referenceId, "legacy removeSyncedLine → void")) {
            return;
        }
        MoneyCategory category = MoneyTransactionLegacySync.categoryFromSource(source);
        MoneyReferenceType referenceType = MoneyTransactionLegacySync.referenceTypeFromSource(source);
        if (accountingFeatureFlags.isRefundEnabled() && isRefundEngineSource(source)) {
            if (refundAccountingBridge.tryVoidByLedgerSource(
                    location.trim(), source, referenceId, "legacy removeTransaction → void")) {
                return;
            }
        }
        if (accountingFeatureFlags.isExpenseEnabled() && isExpenseEngineSource(source)) {
            accountingTransactionService.voidByReference(VoidByReferenceCommand.of(
                    location.trim(),
                    AccountingReference.of(referenceType, referenceId, category),
                    "legacy removeTransaction → void"));
            return;
        }
        legacySync.hardDeleteSyncedLine(source, referenceId);
    }

    public void removeLegacyExpenseDebitLine(Long expenseId) {
        if (expenseId == null) {
            return;
        }
        if (accountingFeatureFlags.isExpenseEnabled()) {
            return;
        }
        moneyTransactionRepository
                .findByReferenceIdAndReferenceTypeAndCategory(
                        expenseId, MoneyReferenceType.expense, MoneyCategory.EXPENSE)
                .ifPresent(moneyTransactionRepository::delete);
    }

    private static boolean isExpenseEngineSource(String source) {
        String s = source.trim().toUpperCase(Locale.ROOT);
        return "EXPENSE".equals(s) || "LOAN_REPAY".equals(s);
    }

    private static boolean isRefundEngineSource(String source) {
        String s = source.trim().toUpperCase(Locale.ROOT);
        return "ADVANCE_REFUND".equals(s)
                || "BILL_EDIT_ADJUSTMENT".equals(s)
                || "BILL_RETURN_WALLET_CREDIT".equals(s)
                || "BILL_RETURN".equals(s);
    }
}
