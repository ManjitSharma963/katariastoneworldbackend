package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillAgentAssignmentResponseDTO {

    private Long billId;
    private String billType;
    private String billNumber;
    private Long agentId;
    private String agentName;
    private String agentCommissionType;
    private Double agentCommissionValue;
    private Double agentCommissionAmount;
    private String agentCommissionStatus;
    private String agentCommissionNotes;
    private boolean cleared;
}
