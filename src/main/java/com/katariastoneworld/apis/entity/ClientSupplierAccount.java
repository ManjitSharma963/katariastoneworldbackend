package com.katariastoneworld.apis.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Per-supplier (client) settings for the client-purchases module: credit limit and default payment terms.
 */
@Entity
@Table(name = "client_supplier_accounts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_client_supplier_loc_key", columnNames = { "location", "client_key" })
}, indexes = {
        @Index(name = "idx_client_supplier_loc", columnList = "location")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientSupplierAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "location", nullable = false, length = 50)
    private String location;

    /** Normalized key (e.g. lower(trim(clientName))) for stable matching. */
    @Column(name = "client_key", nullable = false, length = 256)
    private String clientKey;

    @Column(name = "display_name", length = 200)
    private String displayName;

    /** Max acceptable outstanding payable; null = no limit. */
    @Column(name = "credit_limit", precision = 14, scale = 2)
    private BigDecimal creditLimit;

    /** Days after purchase date when payment is due if purchase has no explicit due date. */
    @Column(name = "payment_terms_days")
    private Integer paymentTermsDays;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
