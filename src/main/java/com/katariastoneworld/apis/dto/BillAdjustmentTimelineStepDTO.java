package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillAdjustmentTimelineStepDTO {

    private String step;
    private String label;
    private String detail;
    private LocalDateTime at;
    private String adjustmentGroupId;
}
