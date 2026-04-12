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

    /**
     * Sum of loan RECEIPT ledger rows in [from, to] recorded via bank transfer or cheque (from entry notes).
     * Used so UI can reflect bank inflows that are not expense rows.
     */
    private BigDecimal loanReceiptsBankChequeInRange;

    /**
     * Sum of loan REPAYMENT ledger rows in [from, to] via bank transfer or cheque (from entry notes).
     */
    private BigDecimal loanRepaymentsBankChequeInRange;

    /**
     * Credits to the “bank channel” budget in [from, to]: loan received (bank/card/cheque), client payments in,
     * customer advance deposits — all recorded with bank transfer, cheque, or other non–cash/UPI modes in the financial ledger.
     */
    private BigDecimal bankCreditsInRange;

    /**
     * Debits from the “bank channel” budget: expenses paid by bank/card/cheque plus supplier bill payments the same way.
     */
    private BigDecimal bankDebitsInRange;

    /**
     * Debits from cash/UPI: expenses paid cash/UPI plus bill payments recorded as cash or UPI in the financial ledger.
     */
    private BigDecimal cashUpiDebitsInRange;

    /**
     * Credits to cash/UPI: loan receipts (cash/UPI), client payment in and advance deposits in cash/UPI (financial ledger).
     */
    private BigDecimal cashUpiCreditsInRange;

    /**
     * Bank opening carried into the first day of the requested range when {@code from} equals {@code to} and that date is today.
     * Same field as {@code daily_budget.bank_opening_balance} after calendar-day rollover.
     */
    private BigDecimal bankOpeningBalanceCarriedForward;

    /**
     * {@link #bankOpeningBalanceCarriedForward} + (bank credits in range − bank debits in range) when single-day summary for today;
     * otherwise null (callers use net movement only).
     */
    private BigDecimal bankBalanceIncludingOpening;

    private String location;
}
