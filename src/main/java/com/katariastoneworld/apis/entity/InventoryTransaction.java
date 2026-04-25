package com.katariastoneworld.apis.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Append-only inventory ledger. {@code products.quantity} remains a cached on-hand value kept in sync.
 */
@Entity
@Table(name = "inventory_transactions", indexes = {
        @Index(name = "idx_inv_txn_product_created", columnList = "product_id,created_at"),
        @Index(name = "idx_inv_txn_ref", columnList = "reference_type,reference_id,bill_kind")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false, length = 32)
    private InventoryTxnType txnType;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private InventoryDirection direction;

    @Column(name = "reference_id")
    private Long referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", length = 32)
    private InventoryReferenceType referenceType;

    /** When {@link #referenceType} is {@link InventoryReferenceType#BILL}, disambiguates GST vs NON_GST bill ids. */
    @Column(name = "bill_kind", length = 16)
    private String billKind;

    @Column(length = 255)
    private String notes;

    @Column(name = "location_id")
    private Long locationId;

    /**
     * Business day for reporting (e.g. bill date for SALE/RETURN tied to a bill). When null, filters fall back to
     * {@link #createdAt}.
     */
    @Column(name = "business_date")
    private LocalDate businessDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
