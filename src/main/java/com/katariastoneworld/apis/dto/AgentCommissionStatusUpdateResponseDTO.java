package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentCommissionStatusUpdateResponseDTO {

    private String billType;
    private Long billId;
    private String status;
    private boolean expenseCreated;
    private Long expenseId;
}
