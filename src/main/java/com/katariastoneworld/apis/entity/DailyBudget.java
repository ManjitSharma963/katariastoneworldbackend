package com.katariastoneworld.apis.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_budget", uniqueConstraints = @UniqueConstraint(columnNames = "location"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Location is required")
    @Column(nullable = false, unique = true, length = 50)
    private String location;

    @NotNull(message = "Amount is required")
    @PositiveOrZero(message = "Budget amount must be positive or zero")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** Remaining budget (amount minus daily expenses). Carried over when setting next day's budget. */
    @Column(name = "remaining_budget", precision = 12, scale = 2)
    private BigDecimal remainingBudget;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
