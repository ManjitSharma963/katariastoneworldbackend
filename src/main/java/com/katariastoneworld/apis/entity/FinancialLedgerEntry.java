package com.katariastoneworld.apis.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "financial_ledger", indexes = {
        @Index(name = "idx_fin_ledger_loc_date", columnList = "location,event_date"),
        @Index(name = "idx_fin_ledger_mode_date", columnList = "payment_mode,event_date"),
        @Index(name = "idx_fin_ledger_event_date_location", columnList = "event_date,location"),
        @Index(name = "idx_fin_ledger_entry_type", columnList = "location,event_date,entry_type"),
        @Index(name = "idx_fin_ledger_entry_type_only", columnList = "entry_type"),
        @Index(name = "idx_event_date", columnList = "event_date"),
        @Index(name = "idx_source_type", columnList = "source_type")
}, uniqueConstraints = @UniqueConstraint(name = "uk_fin_ledger_source_del", columnNames = { "source_type", "source_id",
        "is_deleted" }))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinancialLedgerEntry {

    public enum EventType {
        BILL_PAYMENT,
        /** @deprecated legacy rows; new advances use {@link #CUSTOMER_ADVANCE} */
        ADVANCE_DEPOSIT,
        CUSTOMER_ADVANCE,
        CLIENT_PAYMENT_IN,
        CLIENT_DEBIT,
        EXPENSE,
        SALARY,
        EMPLOYEE_ADVANCE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 16)
    private LedgerEntryType entryType;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "source_id", nullable = false, length = 64)
    private String sourceId;

    @Column(name = "reference_type", length = 32)
    private String referenceType;

    @Column(name = "reference_id", length = 64)
    private String referenceId;

    @Column(name = "location", nullable = false, length = 50)
    private String location;

    @Column(name = "bill_kind", length = 16)
    private String billKind;

    @Column(name = "bill_id")
    private Long billId;

    @Column(name = "customer_id")
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false, length = 32)
    private BillPaymentMode paymentMode;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    /** @deprecated Retained for DB compatibility; application always persists zero. Use {@link #amount} with {@link #entryType} and {@link #paymentMode}. */
    @Column(name = "in_hand_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal inHandAmount;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (eventDate == null) {
            eventDate = LocalDate.now();
        }
        if (isDeleted == null) {
            isDeleted = false;
        }
        if (entryType == null) {
            entryType = LedgerEntryType.CREDIT;
        }
    }
}
