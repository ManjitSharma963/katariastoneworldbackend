package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationReportDTO {
    private String location;
    private String date;
    private Double ledgerTotal;
    private Double budgetNetMovement;
    private Double delta;
    private String level; // OK or WARNING
    private String message;
    @Builder.Default
    private List<ReconciliationCauseDTO> possibleCauses = new ArrayList<>();
}
