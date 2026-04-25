package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyClosingTopCustomerDTO {
    private Long customerId;
    private String customerName;
    private Double salesAmount;
    private Integer billCount;
}
