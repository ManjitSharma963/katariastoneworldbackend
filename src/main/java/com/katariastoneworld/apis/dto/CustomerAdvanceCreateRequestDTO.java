package com.katariastoneworld.apis.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CustomerAdvanceCreateRequestDTO {

    @NotNull
    private Long customerId;

    @NotNull
    @Positive
    private Double amount;

    private String paymentMode;

    private String description;
}
