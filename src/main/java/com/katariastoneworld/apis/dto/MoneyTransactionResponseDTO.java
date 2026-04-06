package com.katariastoneworld.apis.dto;

import com.katariastoneworld.apis.entity.MoneyTxType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class MoneyTransactionResponseDTO {
    private Long id;
    private MoneyTxType type;
    private BigDecimal amount;
    private String category;
    private String paymentMode;
    private String referenceType;
    private String referenceId;
    private String location;
    private LocalDate eventDate;
    private String description;
    private BigDecimal balanceAfter;
    private LocalDateTime createdAt;
}
