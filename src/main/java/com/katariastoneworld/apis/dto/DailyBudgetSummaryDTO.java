package com.katariastoneworld.apis.dto;

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
    private BigDecimal remainingBudget;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
