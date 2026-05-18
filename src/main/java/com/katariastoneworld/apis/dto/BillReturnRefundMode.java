package com.katariastoneworld.apis.dto;

/**
 * How to settle the return value with the customer (not an operating expense).
 */
public enum BillReturnRefundMode {
    /** Physical return only — no money movement, no wallet credit. */
    NO_REFUND,
    /** Cash paid out now (ledger {@code BILL_RETURN} / OUT, CASH rail). */
    CASH_REFUND,
    /** Bank / transfer out (ledger OUT, BANK rail). */
    BANK_REFUND,
    /** Credit customer wallet — no cash/bank movement on this return. */
    WALLET_CREDIT,
    /**
     * Restore customer overpayment vs effective bill obligation to wallet (surplus after returns).
     * Creates adjustment wallet rows; does not rewrite prior advance application history.
     */
    ADVANCE_RESTORE
}
