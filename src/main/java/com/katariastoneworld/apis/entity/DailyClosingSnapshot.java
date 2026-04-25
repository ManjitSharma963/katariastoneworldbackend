package com.katariastoneworld.apis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_closing_snapshot",
        uniqueConstraints = @UniqueConstraint(name = "uk_daily_snapshot_location_date", columnNames = { "location", "snapshot_date" }),
        indexes = {
                @Index(name = "idx_daily_snapshot_location_date", columnList = "location,snapshot_date"),
                @Index(name = "idx_daily_snapshot_recon", columnList = "reconciliation_status")
        })
public class DailyClosingSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String location;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "total_sales", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalSales;

    @Column(name = "total_expenses", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalExpenses;

    @Column(name = "total_collections", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalCollections;

    @Column(name = "in_hand", nullable = false, precision = 14, scale = 2)
    private BigDecimal inHand;

    @Column(name = "reconciliation_status", nullable = false, length = 20)
    private String reconciliationStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public void setSnapshotDate(LocalDate snapshotDate) {
        this.snapshotDate = snapshotDate;
    }

    public BigDecimal getTotalSales() {
        return totalSales;
    }

    public void setTotalSales(BigDecimal totalSales) {
        this.totalSales = totalSales;
    }

    public BigDecimal getTotalExpenses() {
        return totalExpenses;
    }

    public void setTotalExpenses(BigDecimal totalExpenses) {
        this.totalExpenses = totalExpenses;
    }

    public BigDecimal getTotalCollections() {
        return totalCollections;
    }

    public void setTotalCollections(BigDecimal totalCollections) {
        this.totalCollections = totalCollections;
    }

    public BigDecimal getInHand() {
        return inHand;
    }

    public void setInHand(BigDecimal inHand) {
        this.inHand = inHand;
    }

    public String getReconciliationStatus() {
        return reconciliationStatus;
    }

    public void setReconciliationStatus(String reconciliationStatus) {
        this.reconciliationStatus = reconciliationStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
