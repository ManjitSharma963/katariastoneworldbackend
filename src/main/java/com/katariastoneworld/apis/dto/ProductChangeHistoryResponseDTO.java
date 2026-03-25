package com.katariastoneworld.apis.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductChangeHistoryResponseDTO {

    private Long id;
    private Long productId;
    /** Parsed JSON snapshot before save (full product fields). */
    private JsonNode previousSnapshot;
    /** Parsed JSON snapshot after save. */
    private JsonNode newSnapshot;
    private String notes;
    private LocalDateTime createdAt;
}
