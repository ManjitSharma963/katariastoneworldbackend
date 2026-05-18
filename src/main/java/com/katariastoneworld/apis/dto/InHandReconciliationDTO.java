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

    /** Sum of BILL-category CASH/UPI amounts in transactions for the date range. */
    private BigDecimal ledgerBillPaymentInHandTotal;

    /** billCashUpiTotal minus ledgerBillPaymentInHandTotal (should be ~0 when consistent). */
    private BigDecimal difference;

    private boolean match;

    /** Human-readable explanation of what was compared. */
    private String notes;
}
