package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationReportDTO {
    private String location;
    private String date;
    /** Net for the day: sum(CREDIT) − sum(DEBIT) in {@code financial_ledger} for the location. */
    private Double ledgerTotal;
    /** Total DEBIT amount for the day (all outflows), from {@code financial_ledger}. */
    private Double budgetNetMovement;
    /** Absolute difference between sum of credits grouped by payment mode and total credits (sanity check). */
    private Double delta;
    private String level; // OK or WARNING
    private String message;
}
