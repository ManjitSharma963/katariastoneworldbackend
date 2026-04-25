package com.katariastoneworld.apis.entity;

import java.util.Locale;

/**
 * Normalized payment rail for the unified ledger (Phase 1).
 */
public enum LedgerPaymentMode {
    CASH,
    UPI,
    BANK,
    CARD,
    CHEQUE;

    public static LedgerPaymentMode fromBillPaymentMode(BillPaymentMode mode) {
        if (mode == null) {
            return CASH;
        }
        return switch (mode) {
            case CASH -> CASH;
            case UPI -> UPI;
            case BANK_TRANSFER, WALLET, OTHER -> BANK;
            case CHEQUE -> CHEQUE;
        };
    }

    /**
     * Map legacy expense / payroll text (e.g. "bank transfer", "Cash") to unified mode.
     */
    public static LedgerPaymentMode fromLegacyPaymentMethod(String raw) {
        if (raw == null || raw.isBlank()) {
            return CASH;
        }
        String v = raw.trim().toLowerCase(Locale.ROOT).replace('-', ' ');
        if ("cash".equals(v)) {
            return CASH;
        }
        if ("upi".equals(v)) {
            return UPI;
        }
        if (v.contains("cheque") || v.contains("check")) {
            return CHEQUE;
        }
        if (v.contains("card")) {
            return CARD;
        }
        if (v.contains("bank")) {
            return BANK;
        }
        return CASH;
    }
}
