package com.katariastoneworld.apis.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class UpdateStockRequestDTO {

    @NotNull(message = "productId is required")
    private Long productId;

    @NotNull(message = "newQuantity is required")
    @PositiveOrZero(message = "newQuantity must be zero or positive")
    private Double newQuantity;

    private String notes;
}
