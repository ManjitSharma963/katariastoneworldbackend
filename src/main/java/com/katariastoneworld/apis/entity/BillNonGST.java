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
@Table(name = "bills_non_gst")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillNonGST {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "INT")
    private Long id;
    
    @NotBlank(message = "Bill number is required")
    @Column(nullable = false, unique = true, length = 50)
    private String billNumber;
    
    @NotNull(message = "Customer ID is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_bill_non_gst_customer", value = ConstraintMode.NO_CONSTRAINT))
    private Customer customer;
    
    @NotNull(message = "Bill date is required")
    @Column(nullable = false)
    private LocalDate billDate;
    
    @NotNull(message = "Total sqft is required")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalSqft;
    
    @NotNull(message = "Subtotal is required")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;
    
    @NotNull(message = "Service charge is required")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal serviceCharge;
    
    @NotNull(message = "Labour charge is required")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal labourCharge = BigDecimal.ZERO;
    
    @NotNull(message = "Transportation charge is required")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal transportationCharge = BigDecimal.ZERO;
    
    @NotNull(message = "Other expenses is required")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal otherExpenses = BigDecimal.ZERO;
    
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
    private List<BillItemNonGST> items = new ArrayList<>();
    
    /** User (staff) who created this bill. Enables "bills done by a specific user". */
    @Column(name = "created_by_user_id")
    private Long createdByUserId;

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
    
    public void addItem(BillItemNonGST item) {
        items.add(item);
        item.setBill(this);
    }
    
    public void removeItem(BillItemNonGST item) {
        items.remove(item);
        item.setBill(null);
    }
    
    public enum PaymentStatus {
        PENDING, PARTIAL, PAID, CANCELLED
    }
}

