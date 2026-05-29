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
    private LocalDate dueDate;
    private BigDecimal amountPaid;
    /** Amount still owed (zero if overpaid). */
    private BigDecimal amountOutstanding;
    /** Paid more than total — credit with client on this purchase. */
    private BigDecimal amountOverpaid;
    private String notes;
    private String location;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

