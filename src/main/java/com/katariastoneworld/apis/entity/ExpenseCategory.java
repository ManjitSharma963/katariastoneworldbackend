package com.katariastoneworld.apis.entity;

public enum ExpenseCategory {
    DAILY,
    SALARY,
    INVENTORY,
    /** Loan / market borrowing repayments (principal or interest paid out). */
    LOAN,
    MISC;

    public static ExpenseCategory parseFlexible(String raw) {
        if (raw == null || raw.isBlank()) return MISC;
        String v = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        if ("OTHER".equals(v)) return MISC;
        return switch (v) {
            case "DAILY" -> DAILY;
            case "SALARY" -> SALARY;
            case "INVENTORY" -> INVENTORY;
            case "LOAN" -> LOAN;
            case "LOAN_REPAYMENT" -> LOAN;
            default -> MISC;
        };
    }
}

