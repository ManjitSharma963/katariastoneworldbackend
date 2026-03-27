package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.entity.BillKind;
import com.katariastoneworld.apis.entity.BillPaymentMode;
import com.katariastoneworld.apis.entity.FinancialLedgerEntry;
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
        financialLedgerRepository.save(row);
        if (isInHandMode(mode)) {
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
            financialLedgerRepository.save(existing);
        } else if (existing != null) {
            financialLedgerRepository.delete(existing);
        }

        BigDecimal delta = newInHand.subtract(oldInHand).setScale(2, RoundingMode.HALF_UP);
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
        financialLedgerRepository.save(row);
        if (isInHandMode(mode)) {
            dailyBudgetService.recordInHandCollectionFromBill(location.trim(), amt);
        }
    }

    private static boolean isInHandMode(BillPaymentMode mode) {
        return mode == BillPaymentMode.CASH || mode == BillPaymentMode.UPI;
    }
}
