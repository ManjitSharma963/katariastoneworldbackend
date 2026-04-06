package com.katariastoneworld.apis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyBudgetSummaryDTO {

    private Long id;
    private String location;
    private BigDecimal amount;

    /** {@code daily_budget.remaining_budget} when present. */
    @JsonProperty("remainingBudget")
    private BigDecimal remainingBudget;

    /** Ledger net (CREDIT − DEBIT) for the calendar day of {@code updatedAt} / {@code createdAt} on this row. */
    @JsonProperty("netLedgerBalance")
    private BigDecimal netLedgerBalance;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
