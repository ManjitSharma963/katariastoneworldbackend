package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillAdjustmentSessionLineDTO {

    private Long billItemId;
    private String productName;
    private String batchOrLot;
    private String unit;
    private Double soldQuantity;
    private Double returnedAlready;
    private Double returnableQuantity;
    private Double pricePerUnit;
}
