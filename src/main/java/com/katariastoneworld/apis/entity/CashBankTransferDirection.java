package com.katariastoneworld.apis.entity;

public enum CashBankTransferDirection {
    CASH_TO_BANK,
    BANK_TO_CASH;

    public static CashBankTransferDirection parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Direction is required (CASH_TO_BANK or BANK_TO_CASH)");
        }
        String v = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        if ("CASH_TO_BANK".equals(v) || "CASHTOBANK".equals(v)) {
            return CASH_TO_BANK;
        }
        if ("BANK_TO_CASH".equals(v) || "BANKTOCASH".equals(v)) {
            return BANK_TO_CASH;
        }
        throw new IllegalArgumentException("Invalid direction: use CASH_TO_BANK or BANK_TO_CASH");
    }
}
