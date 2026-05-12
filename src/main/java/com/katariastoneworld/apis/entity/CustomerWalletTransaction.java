package com.katariastoneworld.apis.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_wallet_transactions", indexes = {
        @Index(name = "idx_wallet_customer_status", columnList = "customer_id,status"),
        @Index(name = "idx_wallet_customer_created", columnList = "customer_id,created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerWalletTransaction {

    public enum TxnType {
        CREDIT,
        DEBIT
    }

    public enum Status {
        ACTIVE,
        CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "customer_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_customer_wallet_txn_customer", value = ConstraintMode.NO_CONSTRAINT)
    )
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false, length = 16)
    private TxnType txnType;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 64)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", length = 32)
    private BillPaymentMode paymentMode;

    @Column(name = "reference_id", length = 64)
    private String referenceId;

    @Column(name = "reversal_of_id")
    private Long reversalOfId;

    @Column(name = "bill_version_id")
    private Long billVersionId;

    @Column(name = "linked_group_id", length = 64)
    private String linkedGroupId;

    @Column(length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = Status.ACTIVE;
        }
    }
}
