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
@Table(name = "budget_manual_adjustment", indexes = {
        @Index(name = "idx_budget_adj_loc_date", columnList = "location,budget_date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BudgetManualAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "budget_date", nullable = false)
    private LocalDate budgetDate;

    @NotBlank
    @Column(nullable = false, length = 50)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_kind", nullable = false, length = 24)
    private BudgetAdjustmentKind adjustmentKind;

    @NotNull
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", precision = 14, scale = 2)
    private BigDecimal balanceAfter;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
