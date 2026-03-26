package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerAdvanceSummaryDTO {
    /** Sum of original advance amounts recorded for this customer. */
    private Double totalAdvance;
    /** Portion already applied to bills (sum of usage). */
    private Double totalUsed;
    /** Sum of remaining_amount across advance rows. */
    private Double remaining;
}
