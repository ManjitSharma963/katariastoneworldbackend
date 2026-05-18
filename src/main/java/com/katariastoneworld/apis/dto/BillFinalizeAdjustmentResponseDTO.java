package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillFinalizeAdjustmentResponseDTO {

    private String adjustmentGroupId;
    private BillStockReturnResponseDTO stockReturn;
    private BillResponseDTO supplementaryBill;
    private BillAdjustmentSettlementPreviewDTO settlement;
    private List<BillAdjustmentTimelineStepDTO> timeline = new ArrayList<>();
    private BillResponseDTO parentBill;
}
