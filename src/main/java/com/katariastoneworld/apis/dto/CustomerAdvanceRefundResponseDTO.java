package com.katariastoneworld.apis.dto;

import lombok.Data;

@Data
public class CustomerAdvanceRefundResponseDTO {
    private Long customerId;
    private Double refundedAmount;
    private Double remainingWalletBalance;
    private String paymentMode;
    private String description;
    private Long walletTransactionId;
}
