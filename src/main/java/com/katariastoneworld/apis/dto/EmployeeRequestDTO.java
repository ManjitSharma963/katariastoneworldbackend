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
public class EmployeeRequestDTO {
    
    @NotBlank(message = "Employee name is required")
    private String employeeName;
    
    @NotNull(message = "Salary amount is required")
    @Positive(message = "Salary amount must be positive")
    private BigDecimal salaryAmount;
    
    @NotNull(message = "Joining date is required")
    private LocalDate joiningDate;
    
    // Location will be set from JWT token, not from request
}

