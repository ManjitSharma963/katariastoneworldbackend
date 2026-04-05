package com.katariastoneworld.apis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DailyBudgetStatusDTO {

    /** Daily budget amount set for this location */
    private BigDecimal budgetAmount;

    /** Sum of expense rows for this location and date */
    private BigDecimal spentAmount;

    /** Stored remaining_budget column, or budgetAmount minus spent when unset */
    private BigDecimal remainingAmount;

    /**
     * Same-day totals from {@code financial_ledger} (CREDIT / DEBIT / net). Parallel to expense-based fields above.
     */
    private BigDecimal ledgerCreditTotal;
    private BigDecimal ledgerDebitTotal;
    private BigDecimal ledgerNetAmount;

    /** Date for which spent is calculated (default: today) */
    private LocalDate date;

    private String location;
}
