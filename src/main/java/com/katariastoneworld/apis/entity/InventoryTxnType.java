package com.katariastoneworld.apis.entity;

/**
 * Ledger reason for {@link InventoryTransaction}. Persisted as string in DB.
 */
public enum InventoryTxnType {
    OPENING,
    PURCHASE,
    SALE,
    RETURN,
    ADJUSTMENT,
    DAMAGE,
    TRANSFER
}
