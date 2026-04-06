package com.katariastoneworld.apis.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Unified bill row in {@code bills} (GST and non-GST). {@link BillKind} matches {@code bill_payments.bill_kind}.
 */
@Entity
@Table(name = "bills", uniqueConstraints = @UniqueConstraint(name = "uq_bills_loc_num", columnNames = { "location",
        "bill_number" }))
@SQLRestriction("is_deleted = 0")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "bill_kind", nullable = false, length = 16)
    private BillKind billKind;

    @NotBlank
    @Column(name = "bill_number", nullable = false, length = 50)
    private String billNumber;

    @Column(length = 50)
    private String location;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_bills_customer", value = ConstraintMode.NO_CONSTRAINT))
    private Customer customer;

    @NotNull
    @Column(name = "bill_date", nullable = false)
    private LocalDate billDate;

    @NotNull
    @Column(name = "total_sqft", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalSqft;

    @NotNull
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate;

    @Column(name = "tax_amount", precision = 14, scale = 2)
    private BigDecimal taxAmount;

    @NotNull
    @Column(name = "service_charge", nullable = false, precision = 14, scale = 2)
    private BigDecimal serviceCharge;

    @NotNull
    @Column(name = "labour_charge", nullable = false, precision = 14, scale = 2)
    private BigDecimal labourCharge = BigDecimal.ZERO;

    @NotNull
    @Column(name = "transportation_charge", nullable = false, precision = 14, scale = 2)
    private BigDecimal transportationCharge = BigDecimal.ZERO;

    @NotNull
    @Column(name = "other_expenses", nullable = false, precision = 14, scale = 2)
    private BigDecimal otherExpenses = BigDecimal.ZERO;

    @NotNull
    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountAmount;

    @NotNull
    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @NotNull
    @Column(name = "paid_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "payment_method", length = 512)
    private String paymentMethod;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "hsn_code", length = 20)
    private String hsnCode;

    @Column(name = "vehicle_no", length = 50)
    private String vehicleNo;

    @Column(name = "delivery_address", columnDefinition = "TEXT")
    private String deliveryAddress;

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("lineNo ASC")
    private List<BillLineItem> items = new ArrayList<>();

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
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

    public void addItem(BillLineItem item) {
        items.add(item);
        item.setBill(this);
    }

    public void removeItem(BillLineItem item) {
        items.remove(item);
        item.setBill(null);
    }

    public enum PaymentStatus {
        DUE,
        PENDING,
        PARTIAL,
        PAID,
        CANCELLED
    }
}
