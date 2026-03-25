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
@Table(name = "bills_gst")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillGST {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "INT")
    private Long id;
    
    @NotBlank(message = "Bill number is required")
    @Column(nullable = false, unique = true, length = 50)
    private String billNumber;
    
    @NotNull(message = "Customer ID is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_bill_gst_customer", value = ConstraintMode.NO_CONSTRAINT))
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
    
    @NotNull(message = "Tax rate is required")
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate;
    
    @NotNull(message = "Tax amount is required")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal taxAmount;
    
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
    
    /** Human-readable summary of split payments; can exceed short VARCHARs — use DB migration + length here. */
    @Column(length = 512)
    private String paymentMethod;
    
    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Optional bill-level HSN (used on invoice / PDF when line items have no HSN). */
    @Column(name = "hsn_code", length = 20)
    private String hsnCode;

    /** Vehicle / truck number for dispatch (GST invoice). */
    @Column(name = "vehicle_no", length = 50)
    private String vehicleNo;

    /** Delivery address for this sale (may differ from customer registered address). */
    @Column(name = "delivery_address", columnDefinition = "TEXT")
    private String deliveryAddress;
    
    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<BillItemGST> items = new ArrayList<>();
    
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
    
    public void addItem(BillItemGST item) {
        items.add(item);
        item.setBill(this);
    }
    
    public void removeItem(BillItemGST item) {
        items.remove(item);
        item.setBill(null);
    }
    
    public enum PaymentStatus {
        /** No amount recorded in {@code bill_payments} (unpaid / on credit). */
        DUE,
        PENDING,
        PARTIAL,
        PAID,
        CANCELLED
    }
}

