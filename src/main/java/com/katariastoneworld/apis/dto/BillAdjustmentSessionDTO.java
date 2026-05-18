package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillAdjustmentSessionDTO {

    private String adjustmentGroupId;
    private BillResponseDTO originalBill;
    private List<BillAdjustmentSessionLineDTO> lines = new ArrayList<>();
    private BillReturnSummaryDTO returnSummary;
    private List<BillStockReturnHistoryDTO> returnHistory = new ArrayList<>();
    private List<BillSupplementarySummaryDTO> supplementaryBills = new ArrayList<>();
    private BillAdjustmentSettlementPreviewDTO settlementPreview;
    private Double advanceUsed;
    private Double totalPaid;
    private String billLifecycleStatus;
    private String paymentStatus;
}
