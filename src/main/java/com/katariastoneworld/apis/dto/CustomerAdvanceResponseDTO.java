package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerAdvanceResponseDTO {
    private Long id;
    private Long customerId;
    private Double amount;
    private Double remainingAmount;
    private String paymentMode;
    private String description;
    private LocalDateTime createdAt;
}
