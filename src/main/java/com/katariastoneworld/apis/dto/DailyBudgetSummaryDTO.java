package com.katariastoneworld.apis.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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
    /**
     * Ledger net for today at this location (CREDIT − DEBIT). Not read from {@code daily_budget.remaining_budget}.
     */
    @JsonProperty("netLedgerBalance")
    @JsonAlias({ "remainingBudget", "remaining_budget" })
    private BigDecimal netLedgerBalance;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
