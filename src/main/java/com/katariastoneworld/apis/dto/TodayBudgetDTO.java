package com.katariastoneworld.apis.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TodayBudgetDTO {
    private LocalDate date;
    private String location;
    private BigDecimal openingBalance;
    private BigDecimal manualAdjustmentTotal;
    private BigDecimal currentBalance;
    private BigDecimal closingBalance;
}
