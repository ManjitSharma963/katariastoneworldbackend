package com.katariastoneworld.apis.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PayrollAdvanceRequestDTO {
    @NotNull
    @Positive
    private BigDecimal amount;

    /** Optional; defaults to today. */
    private LocalDate date;

    /** CASH/UPI/BANK_TRANSFER/CHEQUE/OTHER (also accepts legacy strings like cash, bank, upi). */
    private String paymentMode;

    private String notes;
}

