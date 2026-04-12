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
public class LoanLedgerEntryResponseDTO {
    private Long id;
    private String entryType;
    private BigDecimal amount;
    private LocalDate entryDate;
    private String notes;
    private Long expenseId;
    private LocalDateTime createdAt;
}
