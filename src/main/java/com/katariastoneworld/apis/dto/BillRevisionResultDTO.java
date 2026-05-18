package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a bill revision mutation including post-commit integrity verification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillRevisionResultDTO {

    private BillResponseDTO bill;

    private BillRevisionIntegrityReportDTO integrity;
}
