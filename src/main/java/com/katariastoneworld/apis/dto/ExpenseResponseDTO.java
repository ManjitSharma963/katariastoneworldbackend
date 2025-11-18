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
public class ExpenseResponseDTO {
    
    private Long id;
    private String type;
    private String category;
    private LocalDate date;
    private String description;
    private BigDecimal amount;
    private String paymentMethod;
    private Long employeeId;
    private String employeeName;
    private String month;
    private Boolean settled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

