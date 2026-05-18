package com.katariastoneworld.apis.entity;

/**
 * Append-only audit events for bill lifecycle (see {@code bill_events}).
 */
public enum BillEventType {
    BILL_EDITED,
    ITEM_ADDED,
    ITEM_REMOVED,
    QUANTITY_INCREASED,
    QUANTITY_DECREASED,
    ADVANCE_RECALCULATED,
    PAYMENT_ADJUSTED,
    REFUND_CREATED,
    STORE_CREDIT_CREATED,
    /** Physical stock return document saved ({@code bill_inventory_returns}). */
    STOCK_RETURN_RECORDED,
    /** Bill cancelled via delete flow (payments reversed, stock restored). */
    BILL_CANCELLED,

    /** Explicit audit note attached to the latest {@code bill_versions} head (non-mutating marker). */
    REVISION_AUDIT
}
