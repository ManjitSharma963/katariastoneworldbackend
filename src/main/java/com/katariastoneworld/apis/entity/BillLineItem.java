package com.katariastoneworld.apis.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bill_line_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false, foreignKey = @ForeignKey(name = "fk_line_bill", value = ConstraintMode.NO_CONSTRAINT))
    private Bill bill;

    @NotNull
    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", foreignKey = @ForeignKey(name = "fk_line_product", value = ConstraintMode.NO_CONSTRAINT))
    private Product product;

    @NotBlank
    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "product_image_url", length = 500)
    private String productImageUrl;

    @Column(name = "product_type", length = 50)
    private String productType;

    @NotNull
    @Positive
    @Column(name = "price_per_sqft", nullable = false, precision = 14, scale = 2)
    private BigDecimal pricePerUnit;

    @NotNull
    @Positive
    @Column(name = "sqft_ordered", nullable = false, precision = 14, scale = 2)
    private BigDecimal quantity;

    @Column(length = 50)
    private String unit;

    @NotNull
    @Column(name = "item_total_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal itemTotalPrice;

    @Column(name = "hsn_number", length = 20)
    private String hsnNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (itemTotalPrice == null && pricePerUnit != null && quantity != null) {
            itemTotalPrice = pricePerUnit.multiply(quantity);
        }
    }
}
