package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryHistoryResponseDTO {

    private Long id;
    private Long productId;
    private String actionType;
    private Double quantityChanged;
    private Double previousQuantity;
    private Double newQuantity;
    private Long referenceId;
    private String notes;
    private LocalDateTime createdAt;
}
