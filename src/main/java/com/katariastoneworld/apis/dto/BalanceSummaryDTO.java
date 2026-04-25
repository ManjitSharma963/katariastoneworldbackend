package com.katariastoneworld.apis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BalanceSummaryDTO {

    /** Net cash + UPI: credits minus debits on {@link com.katariastoneworld.apis.entity.LedgerPaymentMode#CASH} and {@code UPI}. */
    private BigDecimal inHand;

    /** Net bank rails: credits minus debits on {@code BANK}, {@code CARD}, {@code CHEQUE}. */
    private BigDecimal bank;

    private BigDecimal total;

    /**
     * Sum of {@link com.katariastoneworld.apis.entity.LedgerTransactionType#DEBIT} amounts today on CASH+UPI
     * (expenses, client payments out, payroll cash, etc.).
     */
    private BigDecimal todayDebitCashUpi;

    /**
     * Sum of DEBIT amounts today on BANK+CARD+CHEQUE rails (includes bank client/supplier payments not stored as {@code expenses} rows).
     */
    private BigDecimal todayDebitBank;
}
