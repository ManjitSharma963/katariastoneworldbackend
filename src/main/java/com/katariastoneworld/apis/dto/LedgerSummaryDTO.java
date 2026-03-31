package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Location-scoped ledger totals for dashboards (single source of truth from {@code financial_ledger}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerSummaryDTO {

    private String location;
    private LocalDate date;
    private LocalDate dateTo;
    private Double totalCredit;
    private Double totalDebit;
    private Double netBalance;
    /** Sum of DEBIT rows with {@code source_type = EXPENSE} (manual expenses only). */
    private Double expenseDebitTotal;
}
