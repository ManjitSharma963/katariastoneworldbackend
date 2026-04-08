package com.katariastoneworld.apis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Server-computed daily budget figures for a date range (no client-side replay of capped event lists).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DailyBudgetCalculatedSummaryDTO {

    private LocalDate from;
    private LocalDate to;

    /** Remaining cash in hand as of {@link #remainingAsOfDate} (today from daily_budget; past from last event closing). */
    private BigDecimal remainingAmount;

    /**
     * When {@code to} is in the future, remaining is computed as of this date (min(to, today)).
     */
    private LocalDate remainingAsOfDate;

    /**
     * Sum of {@code spent_amount} for EXPENSE_DEBIT and EXPENSE_CREDIT events with {@code date} in [from, to] inclusive.
     */
    private BigDecimal expenseFromEventsInRange;

    /**
     * First {@code daily_budget_events} row that calendar day (by {@code created_at}): {@code opening_balance} — matches Reports "opening budget".
     */
    private BigDecimal openingBalanceForDay;

    /** Daily budget cap from daily_budget row (same field used when editing budget for that date). */
    private BigDecimal budgetAmount;

    /** Total of expense rows for remainingAsOfDate — aligns with Daily Closing report total expenses for one day. */
    private BigDecimal spentAmount;

    private String location;
}
