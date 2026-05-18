package com.katariastoneworld.apis.constants;

/**
 * Allowed values for {@code bills_non_gst.bill_status} — bill <strong>lifecycle</strong>
 * only. This is intentionally separate from {@code payment_status} / paid amounts.
 */
public final class BillLifecycleStatus {

    public static final String DRAFT = "DRAFT";
    public static final String HOLD = "HOLD";
    /** Settled / locked invoice ready for returns, payments, cancellation. */
    public static final String FINALIZED = "FINALIZED";
    public static final String COMPLETED = "COMPLETED";
    /** Partially fulfilled / open obligation (legacy rows may use payment_status instead). */
    public static final String PARTIAL = "PARTIAL";
    /** Fully settled at bill level (legacy; payment_status PAID is authoritative for collections). */
    public static final String PAID = "PAID";
    public static final String PARTIALLY_RETURNED = "PARTIALLY_RETURNED";
    /** Preferred label for a bill with all lines fully returned to stock. */
    public static final String RETURNED = "RETURNED";
    /** @deprecated Prefer {@link #RETURNED}; retained for existing rows. */
    public static final String FULLY_RETURNED = "FULLY_RETURNED";
    /** Parent bill has at least one linked supplementary (exchange / add-on) child. */
    public static final String ADJUSTED = "ADJUSTED";
    /** Legacy create status; new bills use {@link #COMPLETED}. */
    public static final String ACTIVE = "ACTIVE";
    public static final String EXCHANGED = "EXCHANGED";
    /** Admin / workflow lock: no line, payment, or replace edits until cleared. */
    public static final String LOCKED = "LOCKED";
    public static final String CANCELLED = "CANCELLED";
    public static final String SUPERSEDED = "SUPERSEDED";

    private BillLifecycleStatus() {
    }

    /** True if {@code value} is one of the supported lifecycle codes (case-sensitive). */
    public static boolean isKnown(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return switch (value) {
            case DRAFT, HOLD, FINALIZED, COMPLETED, ACTIVE, PARTIAL, PAID, PARTIALLY_RETURNED, RETURNED, FULLY_RETURNED,
                    ADJUSTED, EXCHANGED, LOCKED, CANCELLED, SUPERSEDED ->
                true;
            default -> false;
        };
    }

    public static boolean isReturned(String billStatus) {
        if (billStatus == null || billStatus.isBlank()) {
            return false;
        }
        String s = billStatus.trim();
        return RETURNED.equalsIgnoreCase(s) || FULLY_RETURNED.equalsIgnoreCase(s);
    }
}
