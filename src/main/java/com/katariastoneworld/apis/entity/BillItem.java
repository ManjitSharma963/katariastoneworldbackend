package com.katariastoneworld.apis.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bill_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "INT")
    private Long id;
    
    @NotNull(message = "Bill is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false, foreignKey = @ForeignKey(name = "fk_bill_item_bill", value = ConstraintMode.NO_CONSTRAINT))
    private Bill bill;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = true, foreignKey = @ForeignKey(name = "fk_bill_item_product", value = ConstraintMode.NO_CONSTRAINT))
    private Product product;
    
    @NotBlank(message = "Product name is required")
    @Column(nullable = false, length = 200)
    private String productName;
    
    @Column(length = 500)
    private String productImageUrl;
    
    @Column(length = 50)
    private String productType;
    
    @NotNull(message = "Price per sqft is required")
    @Positive(message = "Price must be positive")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerSqft;
    
    @NotNull(message = "Sqft ordered is required")
    @Min(value = 1, message = "Sqft ordered must be at least 1")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal sqftOrdered;
    
    @NotNull(message = "Item total price is required")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal itemTotalPrice;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (itemTotalPrice == null && pricePerSqft != null && sqftOrdered != null) {
            itemTotalPrice = pricePerSqft.multiply(sqftOrdered);
        }
    }
}
