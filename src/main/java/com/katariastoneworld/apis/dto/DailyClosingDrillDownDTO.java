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
public class DailyClosingDrillDownDTO {
    @Builder.Default
    private List<DailyClosingTopCustomerDTO> topCustomers = new ArrayList<>();
    @Builder.Default
    private List<DailyClosingTopProductDTO> topProducts = new ArrayList<>();
    @Builder.Default
    private List<DailyClosingPendingDueDTO> highestPendingDues = new ArrayList<>();
    @Builder.Default
    private List<DailyClosingPaymentDelayDTO> paymentDelays = new ArrayList<>();
}
