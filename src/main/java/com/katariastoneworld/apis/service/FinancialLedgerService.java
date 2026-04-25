package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.entity.BillKind;
import com.katariastoneworld.apis.entity.BillPaymentMode;
import com.katariastoneworld.apis.entity.FinancialLedgerEntry;
import com.katariastoneworld.apis.entity.LedgerPaymentMode;
import com.katariastoneworld.apis.entity.LedgerSources;
import com.katariastoneworld.apis.entity.LedgerTransactionType;
import com.katariastoneworld.apis.entity.UnifiedFinancialLedgerEntry;
import com.katariastoneworld.apis.repository.FinancialLedgerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Service
@Transactional
public class FinancialLedgerService {

    @Autowired
    private FinancialLedgerRepository financialLedgerRepository;

    @Autowired
    private DailyBudgetService dailyBudgetService;

    @Autowired
    private UnifiedFinancialLedgerService unifiedFinancialLedgerService;

    /**
     * Phase 2 dual-write: single entry point for {@code unified_financial_ledger} from domain services
     * (expense, loan, payroll, etc.). Legacy {@code financial_ledger} continues to use dedicated methods below.
     */
    public UnifiedFinancialLedgerEntry recordTransaction(
            String location,
            LocalDate date,
            BigDecimal amount,
            LedgerTransactionType type,
            LedgerPaymentMode mode,
            String source,
            Long referenceId,
            String description) {
        return unifiedFinancialLedgerService.recordTransaction(
                location, date, amount, type, mode, source, referenceId, description);
    }

    public void removeTransaction(String location, String source, Long referenceId) {
        unifiedFinancialLedgerService.removeTransaction(location, source, referenceId);
    }

    /**
     * Removes a legacy financial_ledger row by source key (idempotent).
     */
    public void removeLegacyFinancialTransaction(String sourceType, String sourceId) {
        if (sourceType == null || sourceType.isBlank() || sourceId == null || sourceId.isBlank()) {
            return;
        }
        financialLedgerRepository.findBySourceTypeAndSourceId(sourceType, sourceId)
                .ifPresent(financialLedgerRepository::delete);
    }

    public void recordBillPayment(String location, BillKind billKind, Long billId, Long paymentId,
            BillPaymentMode mode, BigDecimal amount, LocalDate paymentDate) {
        if (location == null || location.isBlank() || billKind == null || billId == null || paymentId == null
                || mode == null || amount == null) {
            return;
        }
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String sourceType = "BILL_PAYMENT";
        String sourceId = String.valueOf(paymentId);
        if (financialLedgerRepository.findBySourceTypeAndSourceId(sourceType, sourceId).isPresent()) {
            return; // idempotent: already recorded
        }
        FinancialLedgerEntry row = new FinancialLedgerEntry();
        row.setEventType(FinancialLedgerEntry.EventType.BILL_PAYMENT);
        row.setSourceType(sourceType);
        row.setSourceId(sourceId);
        row.setLocation(location.trim());
        row.setBillKind(billKind.name());
        row.setBillId(billId);
        row.setPaymentMode(mode);
        row.setAmount(amt);
        row.setInHandAmount(isInHandMode(mode) ? amt : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        row.setEventDate(paymentDate != null ? paymentDate : LocalDate.now());
        recordTransaction(
                location.trim(),
                row.getEventDate(),
                amt,
                LedgerTransactionType.CREDIT,
                LedgerPaymentMode.fromBillPaymentMode(mode),
                LedgerSources.BILL,
                paymentId,
                "Customer bill payment billKind=" + billKind + " billId=" + billId);
        financialLedgerRepository.save(row);
        LocalDate today = LocalDate.now();
        LocalDate evt = row.getEventDate() != null ? row.getEventDate() : today;
        if (isInHandMode(mode) && evt.equals(today)) {
            dailyBudgetService.recordInHandCollectionFromBill(location.trim(), amt);
        }
    }

    public void syncBillPayment(String location, BillKind billKind, Long billId, Long paymentId,
            BillPaymentMode mode, BigDecimal amount, LocalDate paymentDate, boolean active) {
        if (location == null || location.isBlank() || billKind == null || billId == null || paymentId == null) {
            return;
        }
        String sourceType = "BILL_PAYMENT";
        String sourceId = String.valueOf(paymentId);
        FinancialLedgerEntry existing = financialLedgerRepository.findBySourceTypeAndSourceId(sourceType, sourceId)
                .orElse(null);
        BigDecimal oldInHand = existing != null && existing.getInHandAmount() != null
                ? existing.getInHandAmount().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        LocalDate today = LocalDate.now();
        LocalDate oldEventDate = existing != null && existing.getEventDate() != null ? existing.getEventDate() : today;
        BigDecimal oldDrawerInHand = oldEventDate.equals(today) ? oldInHand : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal newInHand = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (active && mode != null && amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
            if (existing == null) {
                existing = new FinancialLedgerEntry();
                existing.setEventType(FinancialLedgerEntry.EventType.BILL_PAYMENT);
                existing.setSourceType(sourceType);
                existing.setSourceId(sourceId);
            }
            existing.setLocation(location.trim());
            existing.setBillKind(billKind.name());
            existing.setBillId(billId);
            existing.setPaymentMode(mode);
            existing.setAmount(amt);
            newInHand = isInHandMode(mode) ? amt : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            existing.setInHandAmount(newInHand);
            existing.setEventDate(paymentDate != null ? paymentDate : LocalDate.now());
            BigDecimal uAmt = existing.getAmount() != null ? existing.getAmount() : amt;
            recordTransaction(
                    location.trim(),
                    existing.getEventDate(),
                    uAmt,
                    LedgerTransactionType.CREDIT,
                    LedgerPaymentMode.fromBillPaymentMode(mode),
                    LedgerSources.BILL,
                    paymentId,
                    "Customer bill payment billKind=" + billKind + " billId=" + billId);
            financialLedgerRepository.save(existing);
        } else if (existing != null) {
            removeTransaction(location.trim(), LedgerSources.BILL, paymentId);
            financialLedgerRepository.delete(existing);
        }

        BigDecimal newDrawerInHand = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (active && mode != null && amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            LocalDate newEvt = paymentDate != null ? paymentDate : today;
            if (newEvt.equals(today) && isInHandMode(mode)) {
                newDrawerInHand = amount.setScale(2, RoundingMode.HALF_UP);
            }
        }
        BigDecimal delta = newDrawerInHand.subtract(oldDrawerInHand).setScale(2, RoundingMode.HALF_UP);
        if (delta.compareTo(BigDecimal.ZERO) != 0) {
            dailyBudgetService.adjustBudgetForInHandDelta(location.trim(), delta);
        }
    }

    public void recordAdvanceDeposit(String location, Long customerId, Long advanceId, BillPaymentMode mode, BigDecimal amount,
            LocalDate eventDate) {
        if (location == null || location.isBlank() || advanceId == null || mode == null || amount == null) {
            return;
        }
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String sourceType = "ADVANCE_DEPOSIT";
        String sourceId = String.valueOf(advanceId);
        if (financialLedgerRepository.findBySourceTypeAndSourceId(sourceType, sourceId).isPresent()) {
            return;
        }
        FinancialLedgerEntry row = new FinancialLedgerEntry();
        row.setEventType(FinancialLedgerEntry.EventType.ADVANCE_DEPOSIT);
        row.setSourceType(sourceType);
        row.setSourceId(sourceId);
        row.setLocation(location.trim());
        row.setCustomerId(customerId);
        row.setPaymentMode(mode);
        row.setAmount(amt);
        row.setInHandAmount(isInHandMode(mode) ? amt : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        row.setEventDate(eventDate != null ? eventDate : LocalDate.now());
        recordTransaction(
                location.trim(),
                row.getEventDate(),
                amt,
                LedgerTransactionType.CREDIT,
                LedgerPaymentMode.fromBillPaymentMode(mode),
                LedgerSources.ADVANCE,
                advanceId,
                "Customer advance deposit customerId=" + customerId);
        financialLedgerRepository.save(row);
        if (isInHandMode(mode)) {
            dailyBudgetService.recordInHandCollectionFromBill(location.trim(), amt);
        }
    }

    public void recordClientPaymentIn(String location, String clientId, Long clientTransactionId, BillPaymentMode mode,
            BigDecimal amount, LocalDate eventDate) {
        if (location == null || location.isBlank() || clientTransactionId == null || mode == null || amount == null) {
            return;
        }
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String sourceType = "CLIENT_PAYMENT_IN";
        String sourceId = String.valueOf(clientTransactionId);
        if (financialLedgerRepository.findBySourceTypeAndSourceId(sourceType, sourceId).isPresent()) {
            return;
        }
        FinancialLedgerEntry row = new FinancialLedgerEntry();
        row.setEventType(FinancialLedgerEntry.EventType.CLIENT_PAYMENT_IN);
        row.setSourceType(sourceType);
        row.setSourceId(sourceId);
        row.setLocation(location.trim());
        row.setCustomerId(null);
        row.setPaymentMode(mode);
        row.setAmount(amt);
        row.setInHandAmount(isInHandMode(mode) ? amt : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        row.setEventDate(eventDate != null ? eventDate : LocalDate.now());
        recordTransaction(
                location.trim(),
                row.getEventDate(),
                amt,
                LedgerTransactionType.CREDIT,
                LedgerPaymentMode.fromBillPaymentMode(mode),
                LedgerSources.CLIENT_PAYMENT,
                clientTransactionId,
                "Client payment clientId=" + clientId);
        financialLedgerRepository.save(row);
        if (isInHandMode(mode)) {
            dailyBudgetService.recordInHandCollectionFromBill(location.trim(), amt);
        }
    }

    /**
     * Client module outflow (PAYMENT_OUT / PURCHASE cash movement). Dual-writes {@code financial_ledger} + unified ledger (DEBIT).
     */
    public void recordClientPaymentOut(String location, String clientId, Long clientTransactionId, BillPaymentMode mode,
            BigDecimal amount, LocalDate eventDate) {
        if (location == null || location.isBlank() || clientTransactionId == null || mode == null || amount == null) {
            return;
        }
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String sourceType = "CLIENT_PAYMENT_OUT";
        String sourceId = String.valueOf(clientTransactionId);
        if (financialLedgerRepository.findBySourceTypeAndSourceId(sourceType, sourceId).isPresent()) {
            return;
        }
        FinancialLedgerEntry row = new FinancialLedgerEntry();
        row.setEventType(FinancialLedgerEntry.EventType.CLIENT_PAYMENT_OUT);
        row.setSourceType(sourceType);
        row.setSourceId(sourceId);
        row.setLocation(location.trim());
        row.setCustomerId(null);
        row.setPaymentMode(mode);
        row.setAmount(amt);
        row.setInHandAmount(isInHandMode(mode) ? amt : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        row.setEventDate(eventDate != null ? eventDate : LocalDate.now());
        recordTransaction(
                location.trim(),
                row.getEventDate(),
                amt,
                LedgerTransactionType.DEBIT,
                LedgerPaymentMode.fromBillPaymentMode(mode),
                LedgerSources.CLIENT_OUT,
                clientTransactionId,
                "Client payment out clientId=" + clientId);
        financialLedgerRepository.save(row);
        if (isInHandMode(mode)) {
            dailyBudgetService.adjustRemainingForDailyExpense(location.trim(), amt.negate());
        }
    }

    /**
     * Expense -> financial_ledger master write.
     * For CASH/UPI, in_hand_amount is stored as negative (debit from in-hand pool).
     */
    public void syncExpenseDebit(String location, Long expenseId, BillPaymentMode mode, BigDecimal amount, LocalDate eventDate,
            String description, boolean active) {
        if (location == null || location.isBlank() || expenseId == null) {
            return;
        }
        String sourceType = "EXPENSE_DEBIT";
        String sourceId = String.valueOf(expenseId);

        FinancialLedgerEntry existing = financialLedgerRepository.findBySourceTypeAndSourceId(sourceType, sourceId).orElse(null);
        if (!active || mode == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            if (existing != null) {
                financialLedgerRepository.delete(existing);
            }
            return;
        }

        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        FinancialLedgerEntry row = existing != null ? existing : new FinancialLedgerEntry();
        row.setEventType(FinancialLedgerEntry.EventType.EXPENSE_DEBIT);
        row.setSourceType(sourceType);
        row.setSourceId(sourceId);
        row.setLocation(location.trim());
        row.setBillKind(null);
        row.setBillId(null);
        row.setCustomerId(null);
        row.setPaymentMode(mode);
        row.setAmount(amt);
        row.setInHandAmount(isInHandMode(mode) ? amt.negate() : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        row.setEventDate(eventDate != null ? eventDate : LocalDate.now());
        financialLedgerRepository.save(row);
    }

    private static boolean isInHandMode(BillPaymentMode mode) {
        return mode == BillPaymentMode.CASH || mode == BillPaymentMode.UPI;
    }
}
