package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyClosingTopProductDTO {
    private String productName;
    private String productType;
    private String unit;
    private Double quantitySold;
    private Double salesAmount;
}
