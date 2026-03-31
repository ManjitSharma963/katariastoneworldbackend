package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One bucket: {@code financial_ledger.reference_type} on DEBIT rows (null shown as {@code "(none)"}). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferenceTypeExpenseRowDTO {

    private String referenceType;
    private Double totalAmount;
}
