package com.katariastoneworld.apis.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "loan_ledger_entries", indexes = {
        @Index(name = "idx_loan_ledger_loc_lender", columnList = "location,lender_id"),
        @Index(name = "idx_loan_ledger_expense", columnList = "expense_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoanLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String location;

    @Column(name = "lender_id", nullable = false)
    private Long lenderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private LoanLedgerEntryType entryType;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Set for REPAYMENT rows linked to an expense. */
    @Column(name = "expense_id")
    private Long expenseId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
