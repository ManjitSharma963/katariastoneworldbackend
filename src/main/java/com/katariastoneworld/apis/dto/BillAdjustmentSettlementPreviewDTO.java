package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillAdjustmentSettlementPreviewDTO {

    private Double originalBillAmount;
    private Double cumulativeReturnedValue;
    private Double effectiveBillAmount;
    private Double suggestedRefundVersusEffective;
    private Double advanceUsed;
    private Double totalPaid;
}
