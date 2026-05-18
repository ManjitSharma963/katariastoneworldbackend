package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerAdvanceSummaryDTO {
    /** Sum of customer advance deposits ({@code ADVANCE_DEPOSIT} credits), excluding bill-cancel refunds. */
    private Double totalAdvance;
    /** Amount applied to bills ({@code BILL_PAYMENT} debits). */
    private Double totalUsed;
    /** Active wallet balance (all credits minus all debits). */
    private Double remaining;
    /** Unpaid amount still pending across the customer's active old bills. */
    private Double oldBillPendingAmount;
}
