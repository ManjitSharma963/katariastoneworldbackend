package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillAdjustmentHistoryDTO {

    private Long billId;
    private String billNumber;
    private BillReturnSummaryDTO returnSummary;
    private List<BillStockReturnHistoryDTO> returns = new ArrayList<>();
    private List<BillSupplementarySummaryDTO> supplementaryBills = new ArrayList<>();
    private List<BillAdjustmentTimelineStepDTO> timeline = new ArrayList<>();
    private List<BillEventResponseDTO> events = new ArrayList<>();
}
