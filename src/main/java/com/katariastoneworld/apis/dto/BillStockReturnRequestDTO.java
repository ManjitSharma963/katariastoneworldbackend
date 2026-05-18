package com.katariastoneworld.apis.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillStockReturnRequestDTO {

    private String notes;

    /** Groups return + supplementary + settlement transactions (ADJ-yyyyMMdd-xxxx). */
    private String adjustmentGroupId;

    /**
     * How monetary settlement is handled for this return when it is greater than nominal zero.
     * When omitted, inferred from legacy {@link #refundAmount} / {@link #refundPaymentMode}.
     */
    private BillReturnRefundMode refundMode;

    /**
     * Cash/UPI/bank refunded to the customer for this return (sales return — not an expense).
     * When legacy mode is used, combined with {@link #refundPaymentMode}.
     * Prefer {@link #refundMode} for new clients.
     */
    private BigDecimal refundAmount;

    /** CASH, UPI, or BANK (subset used by legacy callers). Prefer {@link #refundMode}. */
    private String refundPaymentMode;

    @NotEmpty
    @Valid
    private List<BillStockReturnLineRequestDTO> lines;
}
