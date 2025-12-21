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
    
    @NotBlank(message = "Product type is required")
    @Column(nullable = false, length = 100)
    private String productType; // Generic: can be any product type (Marble, Granite, Table, Chair, etc.)
    
    @Column(length = 50)
    private String color;
    
    @NotNull(message = "Price per unit is required")
    @PositiveOrZero(message = "Price must be positive or zero")
    @Column(name = "price_per_sqft", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerUnit; // Generic: can be per sqft, per piece, per set, etc.
    
    @NotNull(message = "Quantity/Stock is required")
    @PositiveOrZero(message = "Quantity must be positive or zero")
    @Column(name = "total_sqft_stock", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity; // Generic: can be sqft, count, etc.
    
    @Column(length = 50)
    private String unit; // e.g., "sqft", "piece", "set", "pair", etc. Defaults to "sqft" for backward compatibility
    
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
    
    @Column(name = "labour_charges", precision = 10, scale = 2)
    private BigDecimal labourCharges;
    
    @Column(name = "rto_fees", precision = 10, scale = 2)
    private BigDecimal rtoFees;
    
    @Column(name = "damage_expenses", precision = 10, scale = 2)
    private BigDecimal damageExpenses;
    
    @Column(name = "others_expenses", precision = 10, scale = 2)
    private BigDecimal othersExpenses;
    
    @Column(name = "price_per_sqft_after", precision = 10, scale = 2)
    private BigDecimal pricePerSqftAfter;
    
    @NotBlank(message = "Location is required")
    @Column(nullable = false, length = 50)
    private String location; // Bhondsi or Tapugada
    
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
}
