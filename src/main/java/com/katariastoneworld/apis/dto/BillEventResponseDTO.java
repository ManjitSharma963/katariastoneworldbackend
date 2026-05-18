package com.katariastoneworld.apis.dto;

import com.katariastoneworld.apis.entity.BillEventType;
import com.katariastoneworld.apis.entity.BillKind;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillEventResponseDTO {
    private Long id;
    private BillKind billKind;
    private Long billId;
    private BillEventType eventType;
    private Long billVersionId;
    private String linkedGroupId;
    private String payloadJson;
    private Long createdBy;
    private LocalDateTime createdAt;
}
