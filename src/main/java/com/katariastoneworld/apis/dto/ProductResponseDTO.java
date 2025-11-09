package com.katariastoneworld.apis.dto;

import com.katariastoneworld.apis.entity.Product;
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
    private Product.ProductType productType;
    private String color;
    private Double pricePerSqft;
    private Double totalSqftStock;
    private String primaryImageUrl;
    private String description;
    private Boolean isFeatured;
    private Boolean isActive;
    private String metaKeywords;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

