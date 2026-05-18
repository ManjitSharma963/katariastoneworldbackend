package com.katariastoneworld.apis.dto;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillFinalizeAdjustmentRequestDTO {

    /** Optional; generated when blank (ADJ-yyyyMMdd-xxxx). */
    private String adjustmentGroupId;

    private String adjustmentType;
    private String adjustmentReason;

    @Valid
    private BillStockReturnRequestDTO stockReturn;

    @Valid
    private BillRequestDTO supplementaryBill;

    /**
     * COLLECT | REFUND | ADVANCE | NONE
     */
    private String settlementMethod;

    private BigDecimal settlementAmount;
    private String paymentMode;
    private LocalDate transactionDate;
    private String reference;
    private String notes;
}
