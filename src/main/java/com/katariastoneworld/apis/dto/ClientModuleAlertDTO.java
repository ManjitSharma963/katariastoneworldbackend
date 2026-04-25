package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientModuleAlertDTO {

    /** OVERDUE | OVER_CREDIT_LIMIT */
    private String alertType;
    private String clientKey;
    private String clientName;
    private String message;
    private BigDecimal outstanding;
    private LocalDate dueDate;
    private BigDecimal creditLimit;
}
