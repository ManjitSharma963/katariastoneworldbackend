package com.katariastoneworld.apis.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class ClientTransactionResponseDTO {
    private Long id;
    private String clientId;
    private String transactionType;
    private BigDecimal amount;
    private String paymentMode;
    private LocalDate transactionDate;
    private String notes;
    private String location;
    private LocalDateTime createdAt;
}

