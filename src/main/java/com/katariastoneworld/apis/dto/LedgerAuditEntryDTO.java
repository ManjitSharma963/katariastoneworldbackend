package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerAuditEntryDTO {

    private Long id;
    private String sourceType;
    private String sourceId;
    private String entryType;
    private Double amount;
    private String paymentMode;
    private LocalDate eventDate;
    private String referenceType;
    private String referenceId;
    private String billKind;
    private Long billId;
    private LocalDateTime createdAt;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    private String updatedByName;
    private Boolean deleted;
}
