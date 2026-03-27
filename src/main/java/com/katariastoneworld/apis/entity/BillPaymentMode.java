package com.katariastoneworld.apis.entity;

import java.util.Locale;

public enum BillPaymentMode {
    CASH,
    UPI,
    BANK_TRANSFER,
    CHEQUE,
    OTHER;

    /**
     * Parse API / legacy text; uses the first token so summaries like {@code "CASH ₹500 | Due: ..."} still map to {@link #CASH}.
     */
    public static BillPaymentMode parseFlexible(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Unknown or empty payment mode");
        }
        String first = raw.trim().split("[\\s,|]+")[0];
        String n = first.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        while (n.contains("__")) {
            n = n.replace("__", "_");
        }
        return switch (n) {
            case "CASH" -> CASH;
            case "UPI" -> UPI;
            case "BANK_TRANSFER", "BANK", "BANKTRANSFER", "NETBANKING", "NET_BANKING", "NEFT", "RTGS", "IMPS" ->
                    BANK_TRANSFER;
            case "CHEQUE", "CHECK", "DD" -> CHEQUE;
            case "OTHER" -> OTHER;
            default -> throw new IllegalArgumentException("Unknown payment mode: " + raw);
        };
    }
}
