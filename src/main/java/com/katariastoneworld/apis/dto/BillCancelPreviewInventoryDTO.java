package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillCancelPreviewInventoryDTO {
    private String productName;
    private Double quantityToRestore;
    private String unit;
}
