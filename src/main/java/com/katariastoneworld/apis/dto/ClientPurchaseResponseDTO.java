package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientPurchaseResponseDTO {
    
    private Long id;
    private String clientName;
    private String purchaseDescription;
    private BigDecimal totalAmount;
    private LocalDate purchaseDate;
    private String notes;
    private String location;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

