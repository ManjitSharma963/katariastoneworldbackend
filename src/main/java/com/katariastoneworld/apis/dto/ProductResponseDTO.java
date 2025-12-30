package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponseDTO {
    
    private Long id;
    private String name;
    private String slug;
    private Long categoryId;
    private String productType; // Generic: can be any product type
    private String color;
    private Double pricePerUnit; // Generic: can be per sqft, per piece, per set, etc.
    private Double quantity; // Generic: can be sqft, count, etc.
    private String unit; // e.g., "sqft", "piece", "set", "pair", etc.
    private String primaryImageUrl;
    private String description;
    private Boolean isFeatured;
    private Boolean isActive;
    private String metaKeywords;
    private Double labourCharges;
    private Double rtoFees;
    private Double damageExpenses;
    private Double othersExpenses;
    private Double pricePerSqftAfter;
    private Double transportationCharge;
    private Double gstCharges;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

