package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationCauseDTO {
    private String causeCode;
    private String title;
    private String details;
    private Double estimatedImpact;
    private Boolean autoResolvable;
    private String resolveAction;
}
