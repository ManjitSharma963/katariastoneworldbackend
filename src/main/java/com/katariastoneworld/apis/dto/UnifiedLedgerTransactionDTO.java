package com.katariastoneworld.apis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One row from {@code transactions} for UI transaction history (balance API). */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UnifiedLedgerTransactionDTO {

    private Long id;
    private LocalDate txnDate;
    private String txnType;
    private BigDecimal amount;
    private String paymentMode;
    /** Ledger sub-type (e.g. {@code ADVANCE_APPLICATION} for wallet applied to a bill — not cash inflow). */
    private String subCategory;
    private String source;
    private Long referenceId;
    private String description;
    private LocalDateTime createdAt;
}
