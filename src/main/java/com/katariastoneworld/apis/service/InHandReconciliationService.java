package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.InHandReconciliationDTO;
import com.katariastoneworld.apis.entity.FinancialLedgerEntry;
import com.katariastoneworld.apis.repository.BillPaymentRepository;
import com.katariastoneworld.apis.repository.FinancialLedgerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Cross-check bill CASH/UPI vs {@code financial_ledger} in-hand (what drives {@code daily_budget_events} adjustments).
 */
@Service
@Transactional(readOnly = true)
public class InHandReconciliationService {

    private static final BigDecimal EPS = new BigDecimal("0.02");

    @Autowired
    private BillPaymentRepository billPaymentRepository;

    @Autowired
    private FinancialLedgerRepository financialLedgerRepository;

    public InHandReconciliationDTO reconcileInHandBillPayments(String location, LocalDate from, LocalDate to) {
        String loc = location != null ? location.trim() : "";
        LocalDate f = from != null ? from : LocalDate.now().minusDays(14);
        LocalDate t = to != null ? to : LocalDate.now();
        if (t.isBefore(f)) {
            t = f;
        }

        BigDecimal gst = billPaymentRepository.sumCashUpiGstForLocationAndPaymentDateRange(loc, f, t);
        BigDecimal nongst = billPaymentRepository.sumCashUpiNonGstForLocationAndPaymentDateRange(loc, f, t);
        BigDecimal billTotal = gst.add(nongst).setScale(2, RoundingMode.HALF_UP);

        BigDecimal ledger = financialLedgerRepository.sumInHandBillPaymentsByLocationAndDateRange(loc, f, t,
                FinancialLedgerEntry.EventType.BILL_PAYMENT);
        if (ledger == null) {
            ledger = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            ledger = ledger.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal diff = billTotal.subtract(ledger).setScale(2, RoundingMode.HALF_UP);
        boolean match = diff.abs().compareTo(EPS) <= 0;

        String notes = "Compares sum of CASH/UPI bill_payments (active bills, non-deleted payments, payment_date in range) "
                + "to sum of financial_ledger.in_hand_amount for BILL_PAYMENT rows (event_date in range). "
                + "When a bill is deleted, payments are soft-deleted and ledger rows are removed; budget is reversed via "
                + "adjustBudgetForInHandDelta (new daily_budget_events row, often IN_HAND_DECREASE). "
                + "Older IN_HAND_INCREASE / IN_HAND_COLLECTION rows are not deleted; net effect is correct if this match is true.";

        InHandReconciliationDTO dto = new InHandReconciliationDTO();
        dto.setLocation(loc);
        dto.setFrom(f);
        dto.setTo(t);
        dto.setBillCashUpiTotal(billTotal);
        dto.setLedgerBillPaymentInHandTotal(ledger);
        dto.setDifference(diff);
        dto.setMatch(match);
        dto.setNotes(notes);
        return dto;
    }
}
