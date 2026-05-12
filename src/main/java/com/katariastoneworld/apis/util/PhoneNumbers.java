package com.katariastoneworld.apis.util;

/**
 * Normalization / validation for Indian 10-digit mobiles stored on master entities (supplier, dealer, etc.).
 */
public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    /**
     * Strips non-digits; must yield exactly 10 digits or throws {@link IllegalArgumentException}.
     */
    public static String requireExactlyTenDigits(String raw, String fieldLabel) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(fieldLabel + " is required");
        }
        String d = raw.replaceAll("\\D", "");
        if (d.length() != 10) {
            throw new IllegalArgumentException(
                    fieldLabel + " must be exactly 10 digits (0–9). Found " + d.length()
                            + " digit(s) after removing spaces/symbols.");
        }
        return d;
    }
}
