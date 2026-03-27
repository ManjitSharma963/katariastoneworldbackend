package com.katariastoneworld.apis.entity;

public enum ExpenseCategory {
    DAILY,
    SALARY,
    INVENTORY,
    MISC;

    public static ExpenseCategory parseFlexible(String raw) {
        if (raw == null || raw.isBlank()) return MISC;
        String v = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        if ("OTHER".equals(v)) return MISC;
        return switch (v) {
            case "DAILY" -> DAILY;
            case "SALARY" -> SALARY;
            case "INVENTORY" -> INVENTORY;
            default -> MISC;
        };
    }
}

