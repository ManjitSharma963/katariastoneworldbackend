package com.katariastoneworld.apis.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One timeline row for UI: transaction or manual adjustment.
 */
@Data
public class CashbookRowDTO {
    private Long id;
    /** "TRANSACTION" or "MANUAL" */
    private String rowKind;
    private LocalDate eventDate;
    private LocalDateTime createdAt;
    /** INCOME / EXPENSE / MANUAL_ADD / MANUAL_SUB / MANUAL_SET */
    private String displayType;
    private String category;
    private BigDecimal amount;
    /** Positive = money in, negative = money out (for display). */
    private BigDecimal signedAmount;
    private String paymentMode;
    private String description;
    private BigDecimal balanceAfter;
    /** When set (e.g. EXPENSE, BILL_PAYMENT), row is synced from another module — avoid editing here. */
    private String referenceType;
    private String referenceId;
}
