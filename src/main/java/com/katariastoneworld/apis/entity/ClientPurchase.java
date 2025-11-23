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
@Table(name = "client_purchases")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientPurchase {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "Client name is required")
    @Column(nullable = false, length = 200)
    private String clientName;
    
    @NotBlank(message = "Purchase description is required")
    @Column(nullable = false, columnDefinition = "TEXT")
    private String purchaseDescription;
    
    @NotNull(message = "Total amount is required")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;
    
    @NotNull(message = "Purchase date is required")
    @Column(nullable = false)
    private LocalDate purchaseDate;
    
    @Column(columnDefinition = "TEXT")
    private String notes;
    
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

