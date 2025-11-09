package com.katariastoneworld.apis.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "sellers")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Seller {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "INT")
    private Long id;
    
    @NotBlank(message = "Seller name is required")
    @Column(name="name", nullable = false, length = 200)
    private String sellerName;
    
    @Column(name="gst_no", length = 15)
    private String gstin;
    
    @Column(columnDefinition = "TEXT")
    private String address;
    
    @Column(length = 50)
    private String mobile;
    
    @Column(length = 200)
    private String subHeader;
    
    @Column(length = 200)
    private String bankName;
    
    @Column(name="account_no", length = 50)
    private String accountNumber;
    
    @Column(length = 20)
    private String ifscCode;
    
    @Column(name="terms", columnDefinition = "TEXT")
    private String termsAndConditions;
    
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

