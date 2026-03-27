package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
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
}
