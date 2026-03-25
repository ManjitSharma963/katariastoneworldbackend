package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillPaymentResponseDTO {

    private Long paymentId;
    private Double amount;
    private String paymentMode;
    private LocalDate paymentDate;
}
