package com.katariastoneworld.apis.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Cash borrowed from market / financier / person: increases today's in-hand budget (same mechanism as bill collections).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoanReceiptRequestDTO {

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    /**
     * How the loan amount was received.
     * Only CASH and UPI increase daily in-hand budget; other modes only affect lender totals.
     * Accepted values: cash, upi, bank_transfer, cheque (case-insensitive).
     */
    private String paymentMode;

    /**
     * Optional: existing lender id — adds this receipt to that lender (more borrowing from same source).
     * When set, {@code lenderName} is ignored for lookup.
     */
    private Long lenderId;

    /** Optional: new or unspecified lender name (find-or-create by name when {@code lenderId} is null). */
    private String lenderName;

    /** Optional free-text note (logged for audit). */
    private String notes;
}
