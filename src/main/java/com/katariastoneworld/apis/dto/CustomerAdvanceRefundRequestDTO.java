package com.katariastoneworld.apis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CustomerAdvanceRefundRequestDTO {

    @NotNull
    private Long customerId;

    @NotNull
    @Positive
    private Double amount;

    /** How cash was returned to the customer (CASH, UPI, BANK_TRANSFER, CHEQUE). */
    @NotBlank(message = "Payment mode is required for advance refund")
    private String paymentMode;

    private String description;
}
