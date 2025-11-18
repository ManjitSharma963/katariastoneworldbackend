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
@Table(name = "expenses")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Expense {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "Expense type is required")
    @Column(nullable = false, length = 50)
    private String type; // daily, salary, advance
    
    @NotBlank(message = "Category is required")
    @Column(nullable = false, length = 100)
    private String category;
    
    @NotNull(message = "Date is required")
    @Column(nullable = false)
    private LocalDate date;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @NotNull(message = "Amount is required")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
    
    @Column(length = 50)
    private String paymentMethod; // cash, bank, etc.
    
    // For salary and advance expenses
    @Column(name = "employee_id")
    private Long employeeId;
    
    @Column(name = "employee_name", length = 200)
    private String employeeName;
    
    // For salary expenses
    @Column(length = 20)
    private String month; // Format: YYYY-MM
    
    // For advance expenses
    @Column(name = "settled")
    private Boolean settled = false;
    
    @NotBlank(message = "Location is required")
    @Column(nullable = false, length = 50)
    private String location; // Bhondsi or Tapugada
    
    @Column(name = "created_at", nullable = true, updatable = false, columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = true, columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

