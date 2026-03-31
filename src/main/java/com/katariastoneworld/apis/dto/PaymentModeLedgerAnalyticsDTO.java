package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/** Ledger analytics: sums grouped by {@code payment_mode} and entry side (CREDIT / DEBIT). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentModeLedgerAnalyticsDTO {

    private String location;
    private LocalDate date;
    private LocalDate dateTo;
    private List<PaymentModeLedgerRowDTO> byPaymentMode;
}
