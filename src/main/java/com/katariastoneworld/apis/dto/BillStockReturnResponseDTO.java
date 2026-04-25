package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillStockReturnResponseDTO {

    private Long returnId;
    private String billKind;
    private Long billId;
    private LocalDateTime createdAt;
    private List<BillStockReturnLineResponseDTO> lines = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BillStockReturnLineResponseDTO {
        private Long billItemId;
        private Double quantityReturned;
    }
}
