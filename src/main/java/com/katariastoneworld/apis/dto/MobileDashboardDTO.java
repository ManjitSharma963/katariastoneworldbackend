package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Single-day snapshot from {@code financial_ledger} only (location from JWT).
 * {@code totalSales} = sum CREDIT; {@code totalExpense} = sum DEBIT; {@code paymentModes} = CREDIT by mode.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MobileDashboardDTO {

    /** ISO-8601 calendar date (e.g. {@code 2026-03-27}). */
    private String date;

    private Double totalSales;

    private Double totalExpense;

    private Double netBalance;

    /** CREDIT amounts grouped by {@code payment_mode} (modes with zero total omitted). */
    private Map<String, Double> paymentModes;
}
