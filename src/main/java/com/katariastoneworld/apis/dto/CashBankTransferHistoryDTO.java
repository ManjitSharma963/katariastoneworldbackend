package com.katariastoneworld.apis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CashBankTransferHistoryDTO {

    private String transferGroupId;
    private LocalDate date;
    /** CASH_TO_BANK or BANK_TO_CASH */
    private String direction;
    private BigDecimal amount;
    private String notes;
    private LocalDateTime createdAt;
}
