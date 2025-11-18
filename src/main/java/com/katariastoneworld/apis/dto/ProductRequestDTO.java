package com.katariastoneworld.apis.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.katariastoneworld.apis.entity.Product;
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
    
    @NotNull(message = "Product type is required")
    @JsonAlias({"product_type", "productTypeString"})
    private String productTypeString;
    
    // Getter that converts string to enum (case-insensitive)
    public Product.ProductType getProductType() {
        if (productTypeString == null) {
            return null;
        }
        try {
            // Convert to title case (first letter uppercase, rest lowercase)
            String normalized = productTypeString.substring(0, 1).toUpperCase() + 
                               productTypeString.substring(1).toLowerCase();
            return Product.ProductType.valueOf(normalized);
        } catch (IllegalArgumentException | StringIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Invalid product type: " + productTypeString + 
                ". Valid types are: Marble, Granite, Tiles, Countertop, Chemical, Other");
        }
    }
    
    @NotNull(message = "Price per sqft is required")
    @PositiveOrZero(message = "Price must be positive or zero")
    @JsonAlias({"price_per_sqft", "pricePerSqft"})
    @JsonDeserialize(using = DoubleDeserializer.class)
    private Double pricePerSqft;
    
    @NotNull(message = "Total sqft stock is required")
    @PositiveOrZero(message = "Total sqft stock must be positive or zero")
    @JsonAlias({"total_sqft_stock", "totalSqftStock"})
    @JsonDeserialize(using = DoubleDeserializer.class)
    private Double totalSqftStock;
    
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
    
    // Location will be set from JWT token, not from request
}
