package com.katariastoneworld.apis.accounting.domain;

/** High-level accounting event kinds (expand per migration phase). */
public enum AccountingEventType {
    EXPENSE_OUT,
    EXPENSE_VOID,
    BILL_PAYMENT_IN,
    BILL_PAYMENT_VOID,
    REFUND_STOCK_RETURN,
    REFUND_ADVANCE,
    REFUND_BILL_RETURN_WALLET,
    REFUND_BILL_EDIT_CREDIT,
    REFUND_ADJUSTMENT,
    ADVANCE_IN,
    ADVANCE_OUT,
    LOAN_DISBURSE,
    LOAN_REPAY,
    BUDGET_ADJUST,
    GENERIC_IN,
    GENERIC_OUT
}
