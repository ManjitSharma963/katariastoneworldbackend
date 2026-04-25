package com.katariastoneworld.apis.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientSupplierAccountRequestDTO {

    @NotBlank(message = "Client name is required")
    private String clientName;

    private String displayName;

    private BigDecimal creditLimit;

    private Integer paymentTermsDays;
}
