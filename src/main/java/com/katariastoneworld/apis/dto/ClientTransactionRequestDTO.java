package com.katariastoneworld.apis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ClientTransactionRequestDTO {
    @NotBlank
    private String clientId;

    @NotBlank
    private String transactionType; // PAYMENT_IN / PAYMENT_OUT / PURCHASE

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotBlank
    private String paymentMode; // CASH / UPI / BANK_TRANSFER / CHEQUE / OTHER

    private LocalDate transactionDate;
    private String notes;
}

