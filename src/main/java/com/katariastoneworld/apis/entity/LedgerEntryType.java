package com.katariastoneworld.apis.entity;

/**
 * Direction of money movement for unified {@code FinancialLedgerService#createEntry} calls.
 * Bill collections are {@link #CREDIT} (cash-in to the business).
 */
public enum LedgerEntryType {
    CREDIT,
    DEBIT
}
