package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.accounting.support.ClientTransactionAccountingBridge;
import com.katariastoneworld.apis.accounting.support.CustomerAdvanceAccountingBridge;
import com.katariastoneworld.apis.accounting.support.RefundAccountingBridge;
import com.katariastoneworld.apis.accounting.support.UnifiedLedgerVoidRouter;
import com.katariastoneworld.apis.entity.BillPaymentMode;
import com.katariastoneworld.apis.entity.LedgerPaymentMode;
import com.katariastoneworld.apis.entity.LedgerSources;
import com.katariastoneworld.apis.entity.LedgerTransactionType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Money movements for domain flows, persisted only in {@code transactions} via {@link MoneyTransactionService}.
 * Bill payment lines are created in {@link BillService} (reference = bill id).
 */
@Service
@Transactional
public class FinancialLedgerService {

    @Autowired
    private MoneyTransactionService moneyTransactionService;

    @Autowired
    private RefundAccountingBridge refundAccountingBridge;

    @Autowired
    private CustomerAdvanceAccountingBridge customerAdvanceAccountingBridge;

    @Autowired
    private ClientTransactionAccountingBridge clientTransactionAccountingBridge;

    @Autowired
    private UnifiedLedgerVoidRouter unifiedLedgerVoidRouter;

    public void recordTransaction(
            String location,
            LocalDate date,
            BigDecimal amount,
            LedgerTransactionType type,
            LedgerPaymentMode mode,
            String source,
            Long referenceId,
            String description) {
        moneyTransactionService.syncFromUnified(location, date, amount, type, mode, source, referenceId, description);
    }

    public void removeTransaction(String location, String source, Long referenceId) {
        if (unifiedLedgerVoidRouter.tryVoid(location, source, referenceId, "transaction removed")) {
            return;
        }
        moneyTransactionService.removeSyncedLine(location, source, referenceId);
    }

    public void removeLegacyFinancialTransaction(String sourceType, String sourceId) {
        if (sourceType == null || sourceType.isBlank() || sourceId == null || sourceId.isBlank()) {
            return;
        }
        if (!"EXPENSE_DEBIT".equalsIgnoreCase(sourceType.trim())) {
            return;
        }
        try {
            moneyTransactionService.removeLegacyExpenseDebitLine(Long.parseLong(sourceId.trim()));
        } catch (NumberFormatException ignored) {
            // ignore
        }
    }

    public void recordAdvanceDeposit(String location, Long customerId, Long advanceId, BillPaymentMode mode, BigDecimal amount,
            LocalDate eventDate) {
        if (location == null || location.isBlank() || advanceId == null || mode == null || amount == null) {
            return;
        }
        BigDecimal amt = amount.setScale(2, java.math.RoundingMode.HALF_UP);
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        customerAdvanceAccountingBridge.postAdvanceDeposit(
                location, customerId, advanceId, mode, amt, eventDate,
                "Customer advance deposit customerId=" + customerId);
    }

    public void recordAdvanceRefund(String location, Long customerId, Long refundTxnId, BillPaymentMode mode, BigDecimal amount,
            LocalDate eventDate) {
        refundAccountingBridge.postAdvanceRefund(
                location, customerId, refundTxnId, mode, amount, eventDate,
                "Customer advance refund customerId=" + customerId);
    }

    /**
     * Bill edit (Non-GST replace): excess paid over new total → customer wallet store credit.
     * {@code transactions} row is OUT (same sense as advance refund); keyed by {@code walletTxnId}.
     */
    public void recordBillEditStoreCredit(String location, Long customerId, Long walletTxnId, BigDecimal amount,
            LocalDate eventDate) {
        refundAccountingBridge.postBillEditStoreCredit(location, customerId, walletTxnId, amount, eventDate);
    }

    /**
     * Physical stock return: return value credited to wallet (synced DEBIT keyed by wallet row id — same sense as bill edit excess).
     */
    public void recordBillReturnWalletCredit(String location, Long customerId, Long walletTxnId, BigDecimal amount,
            LocalDate eventDate) {
        refundAccountingBridge.postBillReturnWalletCredit(location, customerId, walletTxnId, amount, eventDate);
    }

    public void recordClientPaymentIn(String location, String clientId, Long clientTransactionId, BillPaymentMode mode,
            BigDecimal amount, LocalDate eventDate) {
        if (location == null || location.isBlank() || clientTransactionId == null || mode == null || amount == null) {
            return;
        }
        BigDecimal amt = amount.setScale(2, java.math.RoundingMode.HALF_UP);
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        clientTransactionAccountingBridge.postClientPaymentIn(
                location, clientId, clientTransactionId, mode, amt, eventDate);
    }

    public void recordClientPaymentOut(String location, String clientId, Long clientTransactionId, BillPaymentMode mode,
            BigDecimal amount, LocalDate eventDate) {
        if (location == null || location.isBlank() || clientTransactionId == null || mode == null || amount == null) {
            return;
        }
        BigDecimal amt = amount.setScale(2, java.math.RoundingMode.HALF_UP);
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        clientTransactionAccountingBridge.postClientPaymentOut(
                location, clientId, clientTransactionId, mode, amt, eventDate);
    }
}
