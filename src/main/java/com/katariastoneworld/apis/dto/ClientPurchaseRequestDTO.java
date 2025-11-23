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
public class ClientPurchaseRequestDTO {
    
    @NotBlank(message = "Client name is required")
    private String clientName;
    
    @NotBlank(message = "Purchase description is required")
    private String purchaseDescription;
    
    @NotNull(message = "Total amount is required")
    @Positive(message = "Total amount must be positive")
    private BigDecimal totalAmount;
    
    @NotNull(message = "Purchase date is required")
    private LocalDate purchaseDate;
    
    private String notes;
    
    // Location will be set from JWT token, not from request
}

