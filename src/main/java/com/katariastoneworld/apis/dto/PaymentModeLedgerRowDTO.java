package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Per {@code payment_mode}: ledger CREDIT vs DEBIT totals in range. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentModeLedgerRowDTO {

    private String paymentMode;
    private Double creditTotal;
    private Double debitTotal;
    private Double net;
}
