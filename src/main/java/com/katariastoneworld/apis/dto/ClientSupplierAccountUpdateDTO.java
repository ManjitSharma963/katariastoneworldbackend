package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientSupplierAccountUpdateDTO {

    private String displayName;
    private BigDecimal creditLimit;
    private Integer paymentTermsDays;
}
