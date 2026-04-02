package com.katariastoneworld.apis.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_budget_events", indexes = {
        @Index(name = "idx_daily_budget_events_location_date", columnList = "location,date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyBudgetEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Location is required")
    @Column(nullable = false, length = 50)
    private String location;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "opening_balance", precision = 12, scale = 2, nullable = false)
    private BigDecimal openingBalance;

    @Column(name = "closing_balance", precision = 12, scale = 2, nullable = false)
    private BigDecimal closingBalance;

    @Column(name = "spent_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal spentAmount;

    @Column(name = "delta", precision = 12, scale = 2)
    private BigDecimal delta;

    @Column(name = "event_type", length = 40, nullable = false)
    private String eventType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

