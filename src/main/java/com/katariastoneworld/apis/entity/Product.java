package com.katariastoneworld.apis.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "INT")
    private Long id;
    
    @NotBlank(message = "Product name is required")
    @Column(nullable = false, length = 200)
    private String name;
    
    @NotBlank(message = "Slug is required")
    @Column(nullable = false, unique = true, length = 250)
    private String slug;
    
    @Column(name = "category_id", columnDefinition = "INT")
    private Long categoryId;
    
    @NotNull(message = "Product type is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductType productType;
    
    @Column(length = 50)
    private String color;
    
    @NotNull(message = "Price per sqft is required")
    @PositiveOrZero(message = "Price must be positive or zero")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerSqft;
    
    @NotNull(message = "Total sqft stock is required")
    @PositiveOrZero(message = "Total sqft stock must be positive or zero")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalSqftStock;
    
    @NotBlank(message = "Primary image URL is required")
    @Column(nullable = false, length = 500)
    private String primaryImageUrl;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(nullable = false)
    private Boolean isFeatured = false;
    
    @Column(nullable = false)
    private Boolean isActive = true;
    
    @Column(length = 500)
    private String metaKeywords;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public enum ProductType {
        Marble, Granite, Tiles, Countertop, Chemical, Other
    }
}
