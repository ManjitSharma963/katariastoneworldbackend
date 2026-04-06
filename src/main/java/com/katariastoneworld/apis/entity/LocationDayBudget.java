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
@Table(name = "location_day_budget", uniqueConstraints = {
        @UniqueConstraint(name = "uk_location_day_budget_date_loc", columnNames = { "budget_date", "location" })
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationDayBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "budget_date", nullable = false)
    private LocalDate budgetDate;

    @NotBlank
    @Column(nullable = false, length = 50)
    private String location;

    @NotNull
    @Column(name = "opening_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @NotNull
    @Column(name = "manual_adjustment_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal manualAdjustmentTotal = BigDecimal.ZERO;

    @NotNull
    @Column(name = "current_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Column(name = "closing_balance", precision = 14, scale = 2)
    private BigDecimal closingBalance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (openingBalance == null) {
            openingBalance = BigDecimal.ZERO;
        }
        if (manualAdjustmentTotal == null) {
            manualAdjustmentTotal = BigDecimal.ZERO;
        }
        if (currentBalance == null) {
            currentBalance = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
