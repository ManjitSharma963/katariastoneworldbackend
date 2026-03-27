package com.katariastoneworld.apis.entity;

public enum ClientTransactionType {
    PAYMENT_IN,
    PAYMENT_OUT,
    PURCHASE;

    public static ClientTransactionType parseFlexible(String raw) {
        if (raw == null || raw.isBlank()) return PURCHASE;
        String v = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (v) {
            case "PAYMENT_IN" -> PAYMENT_IN;
            case "PAYMENT_OUT" -> PAYMENT_OUT;
            default -> PURCHASE;
        };
    }
}

