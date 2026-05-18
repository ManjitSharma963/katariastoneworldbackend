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

    /** Remaining cash in hand as of {@link #remainingAsOfDate} (from transactions net for CASH/UPI). */
    private BigDecimal remainingAmount;

    /**
     * When {@code to} is in the future, remaining is computed as of this date (min(to, today)).
     */
    private LocalDate remainingAsOfDate;

    /** Sum of CASH/UPI OUT transactions (expenses and similar) in [from, to] inclusive. */
    private BigDecimal expenseFromEventsInRange;

    /** Opening balance for the day (null when not tracked separately). */
    private BigDecimal openingBalanceForDay;

    /** Implied daily cap: remaining + spent for {@link #remainingAsOfDate}. */
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
     * Credits to the bank rail in [from, to]: loan received (bank/card/cheque), client payments in,
     * customer advance deposits — BANK-mode transactions.
     */
    private BigDecimal bankCreditsInRange;

    /**
     * Debits from the “bank channel” budget: expenses paid by bank/card/cheque plus supplier bill payments the same way.
     */
    private BigDecimal bankDebitsInRange;

    /**
     * Debits from cash/UPI: expenses and bill payments recorded as CASH or UPI in transactions.
     */
    private BigDecimal cashUpiDebitsInRange;

    /**
     * Credits to cash/UPI: loan receipts, client payments in, advance deposits (CASH/UPI transactions).
     */
    private BigDecimal cashUpiCreditsInRange;

    /**
     * Bank opening carried into the first day of the requested range (null when not tracked).
     */
    private BigDecimal bankOpeningBalanceCarriedForward;

    /**
     * {@link #bankOpeningBalanceCarriedForward} + (bank credits in range − bank debits in range) when single-day summary for today;
     * otherwise null (callers use net movement only).
     */
    private BigDecimal bankBalanceIncludingOpening;

    private String location;
}
