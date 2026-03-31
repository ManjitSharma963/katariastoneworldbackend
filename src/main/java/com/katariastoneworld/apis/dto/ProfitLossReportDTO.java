package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Profit and loss from {@code financial_ledger}: revenue = sum CREDIT, expense = sum DEBIT, net = revenue − expense
 * (active rows only).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfitLossReportDTO {

    private String location;
    private LocalDate date;
    private LocalDate dateTo;
    private Double revenue;
    private Double expense;
    private Double net;
}
