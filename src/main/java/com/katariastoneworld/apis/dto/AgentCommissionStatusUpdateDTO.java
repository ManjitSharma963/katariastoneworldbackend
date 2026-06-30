package com.katariastoneworld.apis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentCommissionStatusUpdateDTO {

    @NotBlank
    @Size(max = 16)
    private String billType;

    @NotNull
    private Long billId;

    @NotBlank
    @Size(max = 20)
    private String status;
}
