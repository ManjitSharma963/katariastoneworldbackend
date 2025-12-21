package com.katariastoneworld.apis.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductRequestDTO {
    
    @NotBlank(message = "Product name is required")
    private String name;
    
    @NotBlank(message = "Slug is required")
    private String slug;
    
    @NotBlank(message = "Product type is required")
    @JsonAlias({"product_type", "productType", "productTypeString"})
    private String productType; // Generic: can be any product type (Marble, Granite, Table, Chair, etc.)
    
    @NotNull(message = "Price per unit is required")
    @PositiveOrZero(message = "Price must be positive or zero")
    @JsonAlias({"price_per_sqft", "pricePerSqft", "price_per_unit", "pricePerUnit"})
    @JsonDeserialize(using = DoubleDeserializer.class)
    private Double pricePerUnit; // Generic: can be per sqft, per piece, per set, etc.
    
    @NotNull(message = "Quantity/Stock is required")
    @PositiveOrZero(message = "Quantity must be positive or zero")
    @JsonAlias({"total_sqft_stock", "totalSqftStock", "quantity", "stock"})
    @JsonDeserialize(using = DoubleDeserializer.class)
    private Double quantity; // Generic: can be sqft, count, etc.
    
    @NotBlank(message = "Unit is required (e.g., sqft, piece, packet, set, pair, box)")
    @JsonAlias({"unit"})
    private String unit; // e.g., "sqft", "piece", "set", "pair", etc. REQUIRED - specify the measurement unit
    
    @NotBlank(message = "Primary image URL is required")
    @JsonAlias({"primary_image_url", "primaryImageUrl"})
    private String primaryImageUrl;
    
    // Optional fields
    private String color;
    private String description;
    
    @JsonAlias({"category_id", "categoryId"})
    private Long categoryId;
    
    @JsonAlias({"is_featured", "isFeatured"})
    private Boolean isFeatured;
    
    @JsonAlias({"is_active", "isActive"})
    private Boolean isActive;
    
    @JsonAlias({"meta_keywords", "metaKeywords"})
    private String metaKeywords;
    
    @JsonAlias({"labour_charges", "labourCharges"})
    @JsonDeserialize(using = DoubleDeserializer.class)
    private Double labourCharges;
    
    @JsonAlias({"rto_fees", "rtoFees"})
    @JsonDeserialize(using = DoubleDeserializer.class)
    private Double rtoFees;
    
    @JsonAlias({"damage_expenses", "damageExpenses"})
    @JsonDeserialize(using = DoubleDeserializer.class)
    private Double damageExpenses;
    
    @JsonAlias({"others_expenses", "othersExpenses"})
    @JsonDeserialize(using = DoubleDeserializer.class)
    private Double othersExpenses;
    
    @JsonAlias({"price_per_sqft_after", "pricePerSqftAfter"})
    @JsonDeserialize(using = DoubleDeserializer.class)
    private Double pricePerSqftAfter;
    
    // Location will be set from JWT token, not from request
    // role and userRole are from JWT token, not from request body
}
