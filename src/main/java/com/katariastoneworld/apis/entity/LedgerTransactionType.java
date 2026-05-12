package com.katariastoneworld.apis.entity;

/**
 * Direction of money when syncing into {@code transactions} (app-level cash/bank position, not strict accounting).
 */
public enum LedgerTransactionType {
    CREDIT,
    DEBIT
}
