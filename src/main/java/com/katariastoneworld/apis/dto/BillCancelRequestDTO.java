package com.katariastoneworld.apis.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Optional body for bill cancellation (soft delete). Operators may record why the bill was voided.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillCancelRequestDTO {

  /** Dropdown code: CUSTOMER_REQUEST, DUPLICATE, WRONG_ENTRY, STOCK_ISSUE, PRICING_MISTAKE, OTHER */
    private String reasonCode;

    @Size(max = 2000, message = "Cancellation reason must be at most 2000 characters")
    @JsonAlias({ "description", "notes" })
    private String reason;

    /** CASH_REFUND, BANK_TRANSFER, ADJUST_TO_ADVANCE */
    private String refundMode;
}
