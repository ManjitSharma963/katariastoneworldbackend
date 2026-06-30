package com.katariastoneworld.apis.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuditReportStatusDTO {
    private String jobId;
    private String status;
    private int progressPercent;
    private String message;
    private long recordsProcessed;
    private long totalRecords;
    private String filename;
    private String error;
}
