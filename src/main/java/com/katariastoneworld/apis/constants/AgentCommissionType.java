package com.katariastoneworld.apis.constants;

public final class AgentCommissionType {

    public static final String PERCENTAGE = "PERCENTAGE";
    public static final String FIXED = "FIXED";

    private AgentCommissionType() {}

    public static boolean isKnown(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String v = value.trim().toUpperCase();
        return PERCENTAGE.equals(v) || FIXED.equals(v);
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return PERCENTAGE;
        }
        String v = value.trim().toUpperCase();
        return isKnown(v) ? v : PERCENTAGE;
    }
}
