package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillCancelPreviewPaymentDTO {
    private Long paymentId;
    private String paymentDate;
    private String paymentMode;
    private String sourceType;
    private String reference;
    private Double amount;
    private Boolean advancePayment;
}
