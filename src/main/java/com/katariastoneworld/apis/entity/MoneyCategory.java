package com.katariastoneworld.apis.entity;

/**
 * Maps to {@code transactions.category} ENUM:
 * EXPENSE, CLIENT_PAYMENT, LOAN, SALARY, ADVANCE, BILL, BILL_REVERSAL, BILL_RETURN, OTHER.
 */
public enum MoneyCategory {
    EXPENSE,
    CLIENT_PAYMENT,
    LOAN,
    SALARY,
    ADVANCE,
    BILL,
    /** Full bill cancellation — revenue/cash reversal, not an expense. */
    BILL_REVERSAL,
    /** Partial return refund to customer — sales return, not an expense. */
    BILL_RETURN,
    OTHER
}
