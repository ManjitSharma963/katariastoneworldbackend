package com.katariastoneworld.apis.entity;

/**
 * Stock movement reason. Persisted as string in {@code inventory_history.action_type}.
 */
public enum InventoryActionType {
    ADD,
    SALE,
    UPDATE,
    ADJUST
}
