package com.katariastoneworld.apis.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_advance_usage")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerAdvanceUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "advance_id", nullable = false)
    private CustomerAdvance advance;

    @Enumerated(EnumType.STRING)
    @Column(name = "bill_kind", nullable = false, length = 16)
    private BillKind billKind;

    @Column(name = "bill_id", nullable = false)
    private Long billId;

    @Column(name = "amount_used", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountUsed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
