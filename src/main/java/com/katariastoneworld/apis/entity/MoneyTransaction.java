package com.katariastoneworld.apis.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "money_transactions", indexes = {
        @Index(name = "idx_money_tx_loc_date", columnList = "location,event_date,is_deleted"),
        @Index(name = "idx_money_tx_ref", columnList = "reference_type,reference_id,is_deleted")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MoneyTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tx_type", nullable = false, length = 16)
    private MoneyTxType txType;

    @NotNull
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String category;

    @Column(name = "payment_mode", length = 32)
    private String paymentMode;

    @Column(name = "reference_type", length = 32)
    private String referenceType;

    @Column(name = "reference_id", length = 64)
    private String referenceId;

    @NotBlank
    @Column(nullable = false, length = 50)
    private String location;

    @NotNull
    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "balance_after", precision = 14, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (isDeleted == null) {
            isDeleted = false;
        }
    }
}
