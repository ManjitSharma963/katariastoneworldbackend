package com.katariastoneworld.apis.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bills")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Bill {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "INT")
    private Long id;
    
    @NotBlank(message = "Bill number is required")
    @Column(nullable = false, unique = true, length = 50)
    private String billNumber;
    
    @NotNull(message = "Customer ID is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_bill_customer", value = ConstraintMode.NO_CONSTRAINT))
    private Customer customer;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inquiry_id", nullable = true, foreignKey = @ForeignKey(name = "fk_bill_inquiry", value = ConstraintMode.NO_CONSTRAINT))
    private Inquiry inquiry;
    
    @NotNull(message = "Bill date is required")
    @Column(nullable = false)
    private LocalDate billDate;
    
    @NotNull(message = "Total sqft is required")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalSqft;
    
    @NotNull(message = "Subtotal is required")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;
    
    @NotNull(message = "Tax rate is required")
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate;
    
    @NotNull(message = "Tax amount is required")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal taxAmount;
    
    @NotNull(message = "Service charge is required")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal serviceCharge;
    
    @NotNull(message = "Discount amount is required")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount;
    
    @NotNull(message = "Total amount is required")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;
    
    @Column(length = 50)
    private String paymentMethod;
    
    @Column(columnDefinition = "TEXT")
    private String notes;
    
    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<BillItem> items = new ArrayList<>();
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (billDate == null) {
            billDate = LocalDate.now();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public void addItem(BillItem item) {
        items.add(item);
        item.setBill(this);
    }
    
    public void removeItem(BillItem item) {
        items.remove(item);
        item.setBill(null);
    }
    
    public enum PaymentStatus {
        PENDING, PARTIAL, PAID, CANCELLED
    }
}
