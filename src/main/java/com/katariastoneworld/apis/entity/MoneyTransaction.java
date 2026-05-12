package com.katariastoneworld.apis.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Append-only money events in {@code transactions} (see {@code db/transactions_ledger.mysql.sql}).
 * Class name avoids clash with {@code jakarta.persistence.Transactional} / generic "Transaction".
 */
@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MoneyTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date_time", nullable = false)
    private LocalDateTime dateTime;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Convert(converter = MoneyDirectionConverter.class)
    @Column(nullable = false, length = 8)
    private MoneyDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MoneyCategory category;

    @Column(name = "sub_category", length = 50)
    private String subCategory;

    @Column(name = "party_name", length = 150)
    private String partyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false, length = 16)
    private MoneyPaymentMode paymentMode;

    @Column(nullable = false, length = 64)
    private String location;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "party_id")
    private Long partyId;

    @Column(name = "reference_id")
    private Long referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 16)
    private MoneyReferenceType referenceType = MoneyReferenceType.other;

    @Column(name = "txn_type", length = 50)
    private String txnType;

    @Column(name = "bill_version_id")
    private Long billVersionId;

    @Column(name = "reversal_of_id")
    private Long reversalOfId;

    @Column(name = "linked_group_id", length = 64)
    private String linkedGroupId;

    @Column(name = "metadata_json", columnDefinition = "JSON")
    private String metadataJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MoneyTxnStatus status = MoneyTxnStatus.ACTIVE;

    @Column(length = 2000)
    private String notes;

    @Column(name = "transaction_date")
    private LocalDate transactionDate;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime n = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = n;
        }
        if (updatedAt == null) {
            updatedAt = n;
        }
        if (amount != null) {
            amount = amount.setScale(2, RoundingMode.HALF_UP);
        }
        if (transactionDate == null) {
            transactionDate = dateTime != null ? dateTime.toLocalDate() : n.toLocalDate();
        }
        if (isDeleted == null) {
            isDeleted = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
