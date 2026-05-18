package com.katariastoneworld.apis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** One documented stock return against a bill (read-only audit). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillStockReturnHistoryDTO {

    private Long returnId;
    private Long billId;
    private String billKind;
    private LocalDateTime createdAt;
    private String notes;
    private Long createdByUserId;

    @Schema(description = "Commercial return value for this document (proportional settlement).")
    private Double computedReturnAmount;

    @Schema(description = "Money posted for this return (0 if NO_REFUND).")
    private Double postedSettlementAmount;

    private BillReturnRefundMode refundMode;

    private List<BillStockReturnLineHistoryDTO> lines = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BillStockReturnLineHistoryDTO {
        private Long billItemId;
        private Double quantityReturned;
    }
}
