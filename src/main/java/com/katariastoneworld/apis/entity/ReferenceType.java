package com.katariastoneworld.apis.entity;

public enum ReferenceType {
    DIRECT,
    PAYROLL,
    CLIENT;

    public static ReferenceType parseFlexible(String raw) {
        if (raw == null || raw.isBlank()) return DIRECT;
        String v = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (v) {
            case "PAYROLL" -> PAYROLL;
            case "CLIENT" -> CLIENT;
            default -> DIRECT;
        };
    }
}

