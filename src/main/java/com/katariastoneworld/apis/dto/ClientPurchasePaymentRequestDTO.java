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
public class ClientPurchasePaymentRequestDTO {
    
    @NotBlank(message = "Client ID is required")
    private String clientId;
    
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;
    
    @NotNull(message = "Date is required")
    private LocalDate date;
    
    @NotBlank(message = "Payment method is required")
    private String paymentMethod;
    
    private String notes;
}

