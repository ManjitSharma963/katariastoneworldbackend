package com.katariastoneworld.apis.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 32)
    private InventoryActionType actionType;

    @Column(name = "quantity_changed", nullable = false, precision = 12, scale = 2)
    private BigDecimal quantityChanged;

    @Column(name = "previous_quantity", nullable = false, precision = 12, scale = 2)
    private BigDecimal previousQuantity;

    @Column(name = "new_quantity", nullable = false, precision = 12, scale = 2)
    private BigDecimal newQuantity;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(length = 512)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
