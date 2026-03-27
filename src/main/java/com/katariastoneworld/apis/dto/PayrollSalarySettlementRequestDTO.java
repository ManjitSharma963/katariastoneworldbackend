package com.katariastoneworld.apis.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PayrollSalarySettlementRequestDTO {
    /** YYYY-MM */
    @NotBlank
    private String month;

    /** Optional; defaults to today. */
    private LocalDate date;

    /** CASH/UPI/BANK_TRANSFER/CHEQUE/OTHER (also accepts legacy strings like cash, bank, upi). */
    private String paymentMode;

    /**
     * Optional cash/upi/bank/cheque amount you are paying now for salary.
     * If omitted, system pays the remaining month due after applying available advance.
     */
    private BigDecimal cashPaidAmount;

    private String notes;
}

