package com.katariastoneworld.apis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesAgentRequestDTO {

    @NotBlank(message = "Agent name is required")
    @Size(max = 200)
    private String name;

    @Size(max = 20)
    @jakarta.validation.constraints.Pattern(regexp = "^\\d{0,10}$", message = "Phone must be up to 10 digits")
    private String phone;

    @Size(max = 200)
    private String email;

    @Size(max = 20)
    private String defaultCommissionType;

    @PositiveOrZero
    private Double defaultCommissionValue;

    @Size(max = 2000)
    private String notes;

    private Boolean active;
}
