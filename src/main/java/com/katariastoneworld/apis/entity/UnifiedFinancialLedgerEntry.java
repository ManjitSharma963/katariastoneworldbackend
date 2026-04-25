package com.katariastoneworld.apis.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Phase 1 unified money-movement log. Coexists with legacy {@link FinancialLedgerEntry} on {@code financial_ledger}
 * until a later migration reads only this table.
 */
@Entity
@Table(name = "unified_financial_ledger", indexes = {
        @Index(name = "idx_unified_ledger_loc_date", columnList = "location,txn_date"),
        @Index(name = "idx_unified_ledger_source", columnList = "source,reference_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_unified_ledger_loc_src_ref", columnNames = { "location", "source", "reference_id" })
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedFinancialLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "location", nullable = false, length = 50)
    private String location;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false, length = 16)
    private LedgerTransactionType txnType;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false, length = 16)
    private LedgerPaymentMode paymentMode;

    @Column(name = "source", nullable = false, length = 50)
    private String source;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (txnDate == null) {
            txnDate = LocalDate.now();
        }
    }
}
