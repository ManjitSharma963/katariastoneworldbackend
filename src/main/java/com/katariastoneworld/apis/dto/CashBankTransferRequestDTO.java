package com.katariastoneworld.apis.dto;

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
public class CashBankTransferRequestDTO {

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    private BigDecimal amount;

    /**
     * CASH_TO_BANK — deduct cash+UPI, add bank.
     * BANK_TO_CASH — deduct bank, add cash+UPI.
     */
    @NotNull(message = "Direction is required")
    private String direction;

    /** Business date (defaults to today when omitted). */
    private LocalDate date;

    private String notes;
}
