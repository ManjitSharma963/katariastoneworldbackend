package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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
    /** Commercial return value before optional legacy refund override (proportional line + GST/discount allocation). */
    private BigDecimal computedReturnAmount;
    /** Amount booked to wallet / cash-out rail (matches legacy refund amount when legacy fields are used). */
    private BigDecimal postedSettlementAmount;
    private BillReturnRefundMode refundMode;
    private List<BillStockReturnLineResponseDTO> lines = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BillStockReturnLineResponseDTO {
        private Long billItemId;
        private Double quantityReturned;
    }
}
