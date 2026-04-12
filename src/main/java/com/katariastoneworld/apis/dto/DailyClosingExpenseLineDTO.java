package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyClosingExpenseLineDTO {

    private Long id;
    /** Expense {@code type} (e.g. daily, salary). */
    private String expenseType;
    private LocalDate date;
    private String category;
    private Double amount;
    /** Expense payment method (cash/upi/bank/card/cheque/...). */
    private String paymentMethod;
    private String description;
}
