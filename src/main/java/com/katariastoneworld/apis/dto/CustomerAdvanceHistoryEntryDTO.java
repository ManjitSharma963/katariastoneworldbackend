package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerAdvanceHistoryEntryDTO {
    private String type;
    private LocalDateTime createdAt;
    private Double amount;
    private String description;
    private String paymentMode;
    private Long billId;
    private String billKind;
    private Long advanceId;
}
