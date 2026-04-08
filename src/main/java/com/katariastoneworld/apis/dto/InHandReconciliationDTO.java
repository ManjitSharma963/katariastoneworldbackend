package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cross-check: CASH/UPI collected on bills vs ledger-driven in-hand (same scope as daily budget updates for bill payments).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InHandReconciliationDTO {

    private String location;
    private LocalDate from;
    private LocalDate to;

    /** Sum of bill_payments.amount where mode is CASH or UPI, bill not deleted, payment not deleted. */
    private BigDecimal billCashUpiTotal;

    /** Sum of financial_ledger.in_hand_amount for BILL_PAYMENT rows (active ledger entries only). */
    private BigDecimal ledgerBillPaymentInHandTotal;

    /** billCashUpiTotal minus ledgerBillPaymentInHandTotal (should be ~0 when consistent). */
    private BigDecimal difference;

    private boolean match;

    /**
     * Why daily_budget_events may still show rows after a bill is deleted: reversals append IN_HAND_DECREASE /
     * IN_HAND_INCREASE deltas; the ledger row is removed so totals match; event history is append-only.
     */
    private String notes;
}
