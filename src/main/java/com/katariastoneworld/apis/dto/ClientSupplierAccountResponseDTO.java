package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientSupplierAccountResponseDTO {

    private Long id;
    private String location;
    private String clientKey;
    private String displayName;
    private BigDecimal creditLimit;
    private Integer paymentTermsDays;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
