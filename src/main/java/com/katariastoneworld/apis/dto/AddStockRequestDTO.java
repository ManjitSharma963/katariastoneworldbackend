package com.katariastoneworld.apis.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class AddStockRequestDTO {

    @NotNull(message = "productId is required")
    private Long productId;

    @NotNull(message = "quantity is required")
    @Positive(message = "quantity must be positive")
    private Double quantity;

    private String notes;
}
