package com.katariastoneworld.apis.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Immutable audit row written when a bill is cancelled (soft-deleted).
 * Lets operators review cancelled invoices without loading soft-deleted bill entities.
 */
@Entity
@Table(name = "bill_cancellation_logs", indexes = {
        @Index(name = "idx_bill_cancel_loc_bill_date", columnList = "location,bill_date"),
        @Index(name = "idx_bill_cancel_loc_cancelled_at", columnList = "location,cancelled_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillCancellationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_kind", nullable = false, length = 16)
    private String billKind;

    @Column(name = "bill_id", nullable = false)
    private Long billId;

    @Column(name = "bill_number", nullable = false, length = 50)
    private String billNumber;

    @Column(nullable = false, length = 50)
    private String location;

    @Column(name = "bill_date", nullable = false)
    private LocalDate billDate;

    @Column(name = "customer_name", length = 200)
    private String customerName;

    @Column(name = "customer_phone", length = 20)
    private String customerPhone;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    /** Sum of non-wallet payment rows immediately before cancellation. */
    @Column(name = "paid_from_payments", nullable = false, precision = 14, scale = 2)
    private BigDecimal paidFromPayments;

    /** Customer advance applied to the bill before cancellation. */
    @Column(name = "advance_applied", nullable = false, precision = 14, scale = 2)
    private BigDecimal advanceApplied;

    /** CASH + UPI collected on the bill before cancellation (in-hand component). */
    @Column(name = "in_hand_collected", nullable = false, precision = 14, scale = 2)
    private BigDecimal inHandCollected;

    @Column(name = "payment_method_summary", length = 512)
    private String paymentMethodSummary;

    @Column(name = "payment_status", length = 32)
    private String paymentStatus;

    @Column(name = "cancelled_at", nullable = false)
    private LocalDateTime cancelledAt;

    @Column(name = "cancelled_by_user_id")
    private Long cancelledByUserId;

    @PrePersist
    protected void onCreate() {
        if (cancelledAt == null) {
            cancelledAt = LocalDateTime.now();
        }
    }
}
