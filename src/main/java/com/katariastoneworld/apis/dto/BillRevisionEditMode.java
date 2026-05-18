package com.katariastoneworld.apis.dto;

/**
 * How {@link com.katariastoneworld.apis.service.BillRevisionService#editBill} applies a bill revision.
 */
public enum BillRevisionEditMode {
    /** Full PUT replace: payments, lines, customer, charges — uses {@link com.katariastoneworld.apis.service.BillService#replaceBill}. */
    FULL_REPLACE,
    /** Difference-based line quantities / added lines — uses {@link com.katariastoneworld.apis.service.BillService#patchBillLineQuantities}. */
    LINE_QUANTITIES_PATCH
}
