package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillCancelPreviewItemDTO {
    private Long billItemId;
    private String productName;
    private String batchOrLot;
    private Double quantity;
    private String unit;
    private Double rate;
    private Double lineAmount;
}
