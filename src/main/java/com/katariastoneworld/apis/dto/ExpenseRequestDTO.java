package com.katariastoneworld.apis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseRequestDTO {
    
    @NotBlank(message = "Expense type is required")
    private String type; // daily, salary, advance
    
    @NotBlank(message = "Category is required")
    private String category;
    
    @NotNull(message = "Date is required")
    private LocalDate date;
    
    private String description;
    
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;
    
    private String paymentMethod;
    
    // For salary and advance expenses
    private Long employeeId;
    private String employeeName;
    
    // For salary expenses
    private String month; // Format: YYYY-MM
    
    // For advance expenses
    private Boolean settled;
    
    // Location will be set from JWT token, not from request
}

