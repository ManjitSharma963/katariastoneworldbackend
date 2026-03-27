package com.katariastoneworld.apis.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "employee_payroll_ledger", indexes = {
        @Index(name = "idx_emp_payroll_emp_month", columnList = "employee_id,month"),
        @Index(name = "idx_emp_payroll_loc_date", columnList = "location,event_date"),
        @Index(name = "idx_emp_payroll_emp_date", columnList = "employee_id,event_date")
})
@Where(clause = "is_deleted = false")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmployeePayrollLedgerEntry {

    public enum EventType {
        /** Money given to employee that reduces future salary payable. */
        ADVANCE_GIVEN,
        /** Accounting-only application of advance toward a salary month (no cash movement). */
        ADVANCE_APPLIED,
        /** Cash/UPI/Bank/Cheque payment given to employee as salary for a month. */
        SALARY_CASH_PAID
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @NotNull
    @Column(name = "location", nullable = false, length = 50)
    private String location;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private EventType eventType;

    @NotNull
    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", length = 32)
    private BillPaymentMode paymentMode;

    @NotNull
    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    /** YYYY-MM derived from eventDate. */
    @NotNull
    @Column(name = "month", nullable = false, length = 7)
    private String month;

    @Column(name = "notes", length = 512)
    private String notes;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (month == null || month.isBlank()) {
            LocalDate d = eventDate != null ? eventDate : LocalDate.now();
            month = String.format("%04d-%02d", d.getYear(), d.getMonthValue());
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

