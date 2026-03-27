package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentModeTotalsDTO {
    private Double upi;
    private Double cash;
    private Double bankTransfer;
    private Double cheque;
    private Double other;
}
