package com.katariastoneworld.apis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillItemDTO {

    @NotBlank(message = "Item name is required")
    private String itemName;

    @NotBlank(message = "Category is required")
    private String category;

    @NotNull(message = "Price per unit is required")
    @Positive(message = "Price must be positive")
    private Double pricePerUnit;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private Double quantity; // Supports decimal quantities (e.g., 0.5, 2.5 sqft, 1.25 pieces)

    // Optional fields
    private String productImageUrl;
    private Long productId;
    private String unit; // e.g., "sqft", "piece", "packet", "set", etc. - will be taken from product if
                         // not provided
    private Double purchasePrice; // New field to store purchase price from product, if available
    private String hsnNumber; // HSN code from product (for GST bill)
}
