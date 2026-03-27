package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * One allocation of paid amount on a bill. Omit or use zero amount for unused modes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillPaymentRequestDTO {

    private Double amount;

    /** Strict enum: CASH, UPI, BANK_TRANSFER, CHEQUE, OTHER */
    private String paymentMode;

    private LocalDate paymentDate;
}
