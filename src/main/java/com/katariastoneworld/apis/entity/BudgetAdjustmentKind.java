package com.katariastoneworld.apis.entity;

/**
 * Manual budget operations (audited in {@code budget_manual_adjustment}).
 */
public enum BudgetAdjustmentKind {
    /** Add to running balance (e.g. extra float). */
    ADD,
    /** Subtract from running balance. */
    SUBTRACT,
    /** Set absolute balance to {@code amount} (reset / override). */
    SET_BALANCE
}
