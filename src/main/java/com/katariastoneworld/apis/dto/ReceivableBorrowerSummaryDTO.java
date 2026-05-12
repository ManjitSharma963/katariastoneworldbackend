package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReceivableBorrowerSummaryDTO {
    private Long id;
    private String displayName;
    /** Total principal lent (disbursements). */
    private BigDecimal totalLent;
    /** Total repayments received from this borrower. */
    private BigDecimal totalCollected;
    /** Amount still owed to you (lent − collected). */
    private BigDecimal outstanding;
}
