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
@Table(name = "bill_items_non_gst")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillItemNonGST {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "INT")
    private Long id;
    
    @NotNull(message = "Bill is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false, foreignKey = @ForeignKey(name = "fk_bill_item_non_gst_bill", value = ConstraintMode.NO_CONSTRAINT))
    private BillNonGST bill;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = true, foreignKey = @ForeignKey(name = "fk_bill_item_non_gst_product", value = ConstraintMode.NO_CONSTRAINT))
    private Product product;
    
    @NotBlank(message = "Product name is required")
    @Column(nullable = false, length = 200)
    private String productName;
    
    @Column(length = 500)
    private String productImageUrl;
    
    @Column(length = 50)
    private String productType;
    
    @NotNull(message = "Price per unit is required")
    @Positive(message = "Price must be positive")
    @Column(name = "price_per_sqft", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerUnit; // Generic: can be per sqft, per piece, per packet, etc.
    
    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Column(name = "sqft_ordered", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity; // Generic: can be sqft, count, packets, etc.
    
    @Column(length = 50)
    private String unit; // e.g., "sqft", "piece", "packet", "set", etc.
    
    @NotNull(message = "Item total price is required")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal itemTotalPrice;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (itemTotalPrice == null && pricePerUnit != null && quantity != null) {
            itemTotalPrice = pricePerUnit.multiply(quantity);
        }
    }
}

