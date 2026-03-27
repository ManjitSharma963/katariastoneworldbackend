package com.katariastoneworld.apis.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "financial_ledger", uniqueConstraints = {
        @UniqueConstraint(name = "uk_fin_ledger_source", columnNames = { "source_type", "source_id" })
}, indexes = {
        @Index(name = "idx_fin_ledger_loc_date", columnList = "location,event_date"),
        @Index(name = "idx_fin_ledger_mode_date", columnList = "payment_mode,event_date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinancialLedgerEntry {

    public enum EventType {
        BILL_PAYMENT,
        ADVANCE_DEPOSIT,
        CLIENT_PAYMENT_IN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private EventType eventType;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "source_id", nullable = false, length = 64)
    private String sourceId;

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

    @Column(name = "in_hand_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal inHandAmount;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (eventDate == null) {
            eventDate = LocalDate.now();
        }
    }
}
