package com.katariastoneworld.apis.constants;

public final class AgentCommissionStatus {

    public static final String PENDING = "PENDING";
    public static final String PAID = "PAID";
    public static final String CANCELLED = "CANCELLED";

    private AgentCommissionStatus() {}

    public static boolean isKnown(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String v = value.trim().toUpperCase();
        return PENDING.equals(v) || PAID.equals(v) || CANCELLED.equals(v);
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        String v = value.trim().toUpperCase();
        return isKnown(v) ? v : PENDING;
    }
}
