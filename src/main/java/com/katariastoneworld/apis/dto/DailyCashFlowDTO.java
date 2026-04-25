package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyCashFlowDTO {
    private Double opening;
    private Double salesCollection;
    private Double advanceReceived;
    private Double expenses;
    private Double closingBalance;
}
