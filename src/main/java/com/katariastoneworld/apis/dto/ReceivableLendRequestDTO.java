package com.katariastoneworld.apis.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Record money lent to a person/org, or repayment received from them.
 * {@code paymentMode}: cash and upi adjust daily in-hand budget; bank_transfer/cheque affect ledger only.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReceivableLendRequestDTO {

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    /**
     * Accepted values: cash, upi, bank_transfer, cheque (case-insensitive).
     */
    private String paymentMode;

    /** Optional: existing borrower id. When set, {@code borrowerName} is ignored for lookup. */
    private Long borrowerId;

    /** Optional: new borrower name (find-or-create when {@code borrowerId} is null). */
    private String borrowerName;

    private String notes;
}
