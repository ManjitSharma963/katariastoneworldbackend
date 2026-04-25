package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyClosingPendingDueDTO {
    private String billType;
    private Long billId;
    private String billNumber;
    private LocalDate billDate;
    private Long customerId;
    private String customerName;
    private Double dueAmount;
}
