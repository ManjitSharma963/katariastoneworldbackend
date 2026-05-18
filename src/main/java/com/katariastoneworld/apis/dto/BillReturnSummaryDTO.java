package com.katariastoneworld.apis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Read model: original invoice totals vs cumulative stock returns vs effective obligation.
 * <p>
 * Advance used on the bill is <strong>not</strong> recomputed here — it remains whatever was already applied
 * in {@code customer_wallet_transactions} / {@code bill_payments}. {@link #suggestedCustomerRefundVersusEffective}
 * is only {@code advanceUsed + totalPaid - effectiveBillTotal} floored at zero: pay that out via normal refund rails
 * (cash/bank/wallet credit), never by “re-applying” or recreating advance logic.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillReturnSummaryDTO {

    @Schema(description = "Total quantity on invoice lines (same basis as bill totalSqft / line quantities).")
    private Double originalInvoiceQuantity;

    @Schema(description = "Persisted invoice grand total (unchanged by return module).")
    private Double originalInvoiceTotalAmount;

    @Schema(description = "Sum of quantities returned to stock across all return documents for this bill.")
    private Double cumulativeReturnedQuantity;

    @Schema(description = "Commercial value of cumulative returns (proportional lines + tax/discount rules, same as return settlement).")
    private Double cumulativeReturnedValue;

    @Schema(description = "Remaining sold quantity after all returns (per line: sold − returned, summed).")
    private Double effectiveSoldQuantityRemaining;

    @Schema(description = "effectiveBillTotal ≈ originalInvoiceTotalAmount − cumulativeReturnedValue (≥ 0).")
    private Double effectiveBillTotal;

    /**
     * max(0, advanceUsed + totalPaid − effectiveBillTotal). Not a second advance transaction — just the cash
     * overpayment vs the reduced obligation after returns.
     */
    @Schema(description = "Suggested refund to customer vs effective obligation; do not alter advance application rows.")
    private Double suggestedCustomerRefundVersusEffective;
}
