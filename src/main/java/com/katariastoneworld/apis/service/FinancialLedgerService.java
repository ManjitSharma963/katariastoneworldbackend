package com.katariastoneworld.apis.service;

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
        recordTransaction(
                location.trim(),
                eventDate != null ? eventDate : LocalDate.now(),
                amt,
                LedgerTransactionType.CREDIT,
                LedgerPaymentMode.fromBillPaymentMode(mode),
                LedgerSources.ADVANCE,
                advanceId,
                "Customer advance deposit customerId=" + customerId);
    }

    public void recordAdvanceRefund(String location, Long customerId, Long refundTxnId, BillPaymentMode mode, BigDecimal amount,
            LocalDate eventDate) {
        if (location == null || location.isBlank() || refundTxnId == null || mode == null || amount == null) {
            return;
        }
        BigDecimal amt = amount.setScale(2, java.math.RoundingMode.HALF_UP);
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        recordTransaction(
                location.trim(),
                eventDate != null ? eventDate : LocalDate.now(),
                amt,
                LedgerTransactionType.DEBIT,
                LedgerPaymentMode.fromBillPaymentMode(mode),
                LedgerSources.ADVANCE_REFUND,
                refundTxnId,
                "Customer advance refund customerId=" + customerId);
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
        recordTransaction(
                location.trim(),
                eventDate != null ? eventDate : LocalDate.now(),
                amt,
                LedgerTransactionType.CREDIT,
                LedgerPaymentMode.fromBillPaymentMode(mode),
                LedgerSources.CLIENT_PAYMENT,
                clientTransactionId,
                "Client payment clientId=" + clientId);
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
        recordTransaction(
                location.trim(),
                eventDate != null ? eventDate : LocalDate.now(),
                amt,
                LedgerTransactionType.DEBIT,
                LedgerPaymentMode.fromBillPaymentMode(mode),
                LedgerSources.CLIENT_OUT,
                clientTransactionId,
                "Client payment out clientId=" + clientId);
    }
}
