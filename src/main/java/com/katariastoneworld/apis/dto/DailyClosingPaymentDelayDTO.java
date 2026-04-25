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
public class DailyClosingPaymentDelayDTO {
    private String billType;
    private Long billId;
    private String billNumber;
    private LocalDate billDate;
    private Long customerId;
    private String customerName;
    private Integer delayDays;
    private String delayStatus;
    private Double totalAmount;
    private Double paidAmount;
    private Double dueAmount;
}
