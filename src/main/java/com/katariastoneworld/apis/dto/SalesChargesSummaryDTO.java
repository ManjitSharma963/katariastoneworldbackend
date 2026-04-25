package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesChargesSummaryDTO {
    private Double totalSqftSold;
    private Double totalLabourCharge;
    private Double totalOtherExpensesCharge;
}
