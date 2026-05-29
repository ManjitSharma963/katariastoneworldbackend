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
public class ClientPurchaseAddAmountRequestDTO {

    @NotNull(message = "Additional amount is required")
    @Positive(message = "Additional amount must be positive")
    private BigDecimal additionalAmount;

    /** What you bought (appended to purchase description). */
    private String description;

    private LocalDate purchaseDate;

    private String notes;
}
