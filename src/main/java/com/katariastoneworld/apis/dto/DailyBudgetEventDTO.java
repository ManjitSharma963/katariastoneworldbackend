package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyBudgetEventDTO {

    private Long id;
    private String location;
    private LocalDate date;

    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private BigDecimal spentAmount;
    private BigDecimal delta;

    private String eventType;
    private LocalDateTime createdAt;
}

