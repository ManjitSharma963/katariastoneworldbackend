package com.katariastoneworld.apis.dto;

import com.katariastoneworld.apis.entity.MoneyTxType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class MoneyTransactionRequestDTO {

    @NotNull
    private MoneyTxType type;

    @NotNull
    private BigDecimal amount;

    @NotNull
    private String category;

    private String paymentMode;
    private LocalDate eventDate;
    private String description;
    /** Optional; default MANUAL_ENTRY for API-created rows */
    private String referenceType;
    private String referenceId;
}
