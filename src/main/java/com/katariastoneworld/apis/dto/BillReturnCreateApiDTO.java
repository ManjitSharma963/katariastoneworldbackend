package com.katariastoneworld.apis.dto;

import com.katariastoneworld.apis.constants.BillAdjustmentSettlementMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Public API shape for {@code POST …/returns} ({@code items} + {@code returnQty}).
 * Mapped to {@link BillStockReturnRequestDTO} internally.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillReturnCreateApiDTO {

    @NotEmpty
    @Valid
    private List<ReturnItem> items = new ArrayList<>();

    /** CASH | UPI | BANK_TRANSFER | ADVANCE — or legacy enum names. */
    private String refundMode;

    private String notes;
    private String adjustmentGroupId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReturnItem {
        private Long billItemId;
        private Double returnQty;
    }

    public BillStockReturnRequestDTO toStockReturnRequest() {
        BillStockReturnRequestDTO dto = new BillStockReturnRequestDTO();
        dto.setNotes(notes);
        dto.setAdjustmentGroupId(adjustmentGroupId);
        dto.setRefundMode(mapRefundMode(refundMode));
        if (refundMode != null && dto.getRefundMode() == BillReturnRefundMode.CASH_REFUND) {
            String rail = refundMode.trim().toUpperCase();
            if (rail.contains("UPI")) {
                dto.setRefundPaymentMode("UPI");
            } else {
                dto.setRefundPaymentMode("CASH");
            }
        }
        List<BillStockReturnLineRequestDTO> lines = new ArrayList<>();
        if (items != null) {
            for (ReturnItem it : items) {
                if (it == null || it.getBillItemId() == null || it.getReturnQty() == null) {
                    continue;
                }
                BillStockReturnLineRequestDTO ln = new BillStockReturnLineRequestDTO();
                ln.setBillItemId(it.getBillItemId());
                ln.setQuantity(it.getReturnQty());
                lines.add(ln);
            }
        }
        dto.setLines(lines);
        return dto;
    }

    private static BillReturnRefundMode mapRefundMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return BillReturnRefundMode.NO_REFUND;
        }
        String u = raw.trim().toUpperCase();
        if ("CASH".equals(u) || "UPI".equals(u)) {
            return BillReturnRefundMode.CASH_REFUND;
        }
        if ("BANK_TRANSFER".equals(u) || "BANK".equals(u)) {
            return BillReturnRefundMode.BANK_REFUND;
        }
        if ("ADVANCE".equals(u) || BillAdjustmentSettlementMethod.ADVANCE.equals(u)) {
            return BillReturnRefundMode.WALLET_CREDIT;
        }
        if ("NO_REFUND".equals(u) || "NONE".equals(u)) {
            return BillReturnRefundMode.NO_REFUND;
        }
        try {
            return BillReturnRefundMode.valueOf(u);
        } catch (IllegalArgumentException e) {
            return BillReturnRefundMode.NO_REFUND;
        }
    }
}
