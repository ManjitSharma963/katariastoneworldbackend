package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentCommissionHistoryDTO {

    private Long billId;
    private String billType;
    private String billNumber;
    private LocalDate billDate;
    private String customerName;
    private String customerMobileNumber;
    private Double billTotalAmount;
    private String commissionType;
    private Double commissionValue;
    private Double commissionAmount;
    private String commissionStatus;
    private String commissionNotes;
    private String billPaymentStatus;
    private String billLifecycleStatus;
}
