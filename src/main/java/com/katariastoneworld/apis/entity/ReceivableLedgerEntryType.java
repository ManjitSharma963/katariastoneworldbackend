package com.katariastoneworld.apis.entity;

/**
 * Money you lent to a counterparty (they owe you): disbursement increases receivable;
 * repayment received decreases it.
 */
public enum ReceivableLedgerEntryType {
    DISBURSEMENT,
    REPAYMENT_RECEIVED
}
