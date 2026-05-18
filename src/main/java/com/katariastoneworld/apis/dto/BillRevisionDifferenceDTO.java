package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Read-side comparison of current bill state vs totals implied by a proposed {@link BillRequestDTO}
 * (same pricing rules as {@link com.katariastoneworld.apis.service.BillService#replaceBill}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillRevisionDifferenceDTO {

    private Double oldSubtotal;
    private Double proposedNewSubtotal;
    private Double oldTaxAmount;
    private Double proposedNewTaxAmount;

    private Double oldTotalAmount;
    private Double proposedNewTotalAmount;
    private Double totalDelta;

    private Double oldAdvanceUsed;
    private Double oldPaidAmount;
    private Double oldAmountDue;

    /**
     * {@code proposedNewTotal − oldAdvanceUsed − oldPaidAmount} (floored at zero for display).
     * Informational only: after a real replace, advance FIFO and payment lines may differ.
     */
    private Double estimatedDueIfAdvanceAndCashUnchanged;
}
