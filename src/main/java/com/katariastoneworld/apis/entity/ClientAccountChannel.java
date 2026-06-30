package com.katariastoneworld.apis.entity;

/**
 * GST vs Non-GST payable rail for a supplier/client (mirrors sales bill types).
 */
public enum ClientAccountChannel {
    GST,
    NON_GST;

    public static ClientAccountChannel parseFlexible(String raw) {
        if (raw == null || raw.isBlank()) {
            return NON_GST;
        }
        String v = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (v) {
            case "GST", "G_S_T" -> GST;
            case "NON_GST", "NONGST", "NON", "WITHOUT_GST", "WITHOUTGST" -> NON_GST;
            default -> NON_GST;
        };
    }

    public String displayLabel() {
        return this == GST ? "GST" : "Non-GST";
    }
}
