package com.katariastoneworld.apis.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "customers")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Customer {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "INT")
    private Long id;
    
    @NotBlank(message = "Phone number is required")
    @Column(nullable = false, unique = true, length = 20)
    private String phone;
    
    @Column(name = "name", length = 200)
    private String name;
    
    @Column(name = "customer_name", length = 200)
    private String customerName;
    
    @Column(columnDefinition = "TEXT")
    private String address;
    
    @Column(length = 15)
    private String gstin;
    
    @Column(length = 100)
    private String email;
    
    @Column(length = 50)
    private String location; // Bhondsi or Tapugada - optional, can be set when creating customer
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
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

