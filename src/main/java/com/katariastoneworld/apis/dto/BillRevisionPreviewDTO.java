package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side preview for bill replace: totals delta + settlement + optional stock warnings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillRevisionPreviewDTO {

    private BillRevisionDifferenceDTO difference;

    /** e.g. ADDITIONAL_PAYMENT, REFUND_OR_STORE_CREDIT, EVEN, WALLET_COVERS */
    private String settlementKind;

    private String settlementHeadline;

    private Double settlementAmount;

    private Double additionalPaymentRequired;

    private Double refundOrStoreCreditAmount;

    @Builder.Default
    private List<String> stockWarnings = new ArrayList<>();

    @Builder.Default
    private List<String> validationMessages = new ArrayList<>();

    private Boolean canFullReplace;
}
