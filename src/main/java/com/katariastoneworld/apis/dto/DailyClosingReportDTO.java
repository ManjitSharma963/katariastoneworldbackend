package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Daily closing report payload for the REST API. Monetary fields use {@link Double} for JSON interoperability;
 * amounts are computed with {@link java.math.BigDecimal} on the server and rounded to 2 decimal places.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyClosingReportDTO {

    /** Start of report period (inclusive), or the only day when {@link #dateTo} equals this. */
    private LocalDate date;
    /** End of report period (inclusive). When equal to {@link #date}, the report is for a single calendar day. */
    private LocalDate dateTo;
    private String location;

    /** Bills with {@code bill_date = date} (GST + non-GST) for this location. */
    private Integer totalBills;

    /** Sum of {@code total_amount} for those bills. */
    private Double totalSales;

    /**
     * Sum of {@code paid_amount} for bills <strong>issued</strong> on {@code date} (matches "Total Paid" on bills table).
     */
    private Double totalPaidOnBills;

    /**
     * Sum of {@code due_amount} for bills issued on {@code date} (same as {@link #pendingAmount}; duplicate for API clarity).
     */
    private Double totalDueOnBills;

    /**
     * Money received in the period for this location:
     * bill payment rows ({@code bill_payments.payment_date}) + customer advance deposits.
     */
    private Double totalCollected;

    /**
     * Amounts collected in the period by mode (bill payments + advance deposits). Keys typically include
     * {@code CASH}, {@code UPI}, {@code BANK_TRANSFER}, {@code CHEQUE}, and {@code OTHER}.
     */
    @Builder.Default
    private Map<String, Double> paymentSummary = new LinkedHashMap<>();

    /** Non-fatal notices (e.g. large date range, reconciliation mismatch). */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    /** True when sum of {@link #paymentSummary} values matches {@link #totalCollected} within tolerance. */
    private Boolean collectionsReconciliationOk;

    /** Absolute difference between sum(paymentSummary) and totalCollected (for support; should be ~0). */
    private Double collectionsReconciliationDelta;

    /**
     * All DEBIT in the period (ledger): salary, expenses, client debits, etc.
     * Same as {@link #totalOutflow}; kept for older clients.
     */
    private Double totalExpenses;

    /** All DEBIT in the period from {@code financial_ledger} ({@code is_deleted = 0}). */
    private Double totalOutflow;

    /** DEBIT rows with {@code source_type = EXPENSE} only (aligns with manual expense list when ledger is in sync). */
    private Double expenseOnlyTotal;

    /**
     * Sum of new customer advance/token deposits ({@code customer_advance.amount}) with {@code created_at}
     * in the report period for this location.
     */
    private Double totalAdvanceDeposits;

    /**
     * Sum of advance applied to bills ({@code customer_advance_usage.amount_used}) with usage {@code created_at}
     * in the report period for this location.
     */
    private Double totalAdvanceAppliedOnBills;
    
    /**
     * Net advance for the report period: {@code totalAdvanceDeposits - totalAdvanceAppliedOnBills}.
     * Positive means advance pool increased; negative means more advance was consumed than deposited in period.
     */
    private Double totalAdvanceAvailable;

    /**
     * For bills <strong>billed</strong> on {@code date}: sum of {@code max(0, total_amount - paid)} where
     * {@code paid} is all-time payments on that bill (plus legacy PAID inference when no rows).
     */
    private Double pendingAmount;

    /** In-hand collections (cash + UPI) on period minus expenses (simple day close). */
    private Double inHandAmount;

    /** @deprecated Backward compatibility alias of {@link #inHandAmount}. */
    @Deprecated
    private Double cashInHand;

    @Builder.Default
    private List<DailyClosingBillLineDTO> bills = new ArrayList<>();

    /** Expense rows for {@code expenses.date = date} at this location (today's expenses list). */
    @Builder.Default
    private List<DailyClosingExpenseLineDTO> expenseLines = new ArrayList<>();
}
