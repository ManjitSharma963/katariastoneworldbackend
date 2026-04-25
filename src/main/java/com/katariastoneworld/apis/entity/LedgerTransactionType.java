package com.katariastoneworld.apis.entity;

/**
 * Direction of money for {@link UnifiedFinancialLedgerEntry} (app-level cash/bank position, not strict accounting).
 */
public enum LedgerTransactionType {
    CREDIT,
    DEBIT
}
