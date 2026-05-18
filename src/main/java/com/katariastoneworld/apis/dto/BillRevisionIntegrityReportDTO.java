package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Read-only cross-checks on a bill's read model (totals vs payments vs advance). Used for ops / §17-style verification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillRevisionIntegrityReportDTO {

    private boolean consistent;

    private List<String> findings;
}
