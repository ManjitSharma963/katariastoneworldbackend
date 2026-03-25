package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyClosingExpenseLineDTO {

    private Long id;
    /** Expense {@code type} (e.g. daily, salary). */
    private String expenseType;
    private String category;
    private Double amount;
    private String description;
}
