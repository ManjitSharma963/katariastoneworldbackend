package com.katariastoneworld.apis.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_reservations", indexes = {
        @Index(name = "idx_inv_resv_product_status", columnList = "product_id,status"),
        @Index(name = "idx_inv_resv_bill", columnList = "reference_id,bill_kind,status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "reserved_quantity", nullable = false, precision = 14, scale = 2)
    private BigDecimal reservedQuantity;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "bill_kind", length = 16)
    private String billKind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InventoryReservationStatus status = InventoryReservationStatus.ACTIVE;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = InventoryReservationStatus.ACTIVE;
        }
    }
}
