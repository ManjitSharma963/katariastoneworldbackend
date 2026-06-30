package com.katariastoneworld.apis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignBillAgentCommissionDTO {

    @NotBlank
    @Size(max = 16)
    private String billType;

    /** Required unless {@link #billNumber} is provided. */
    private Long billId;

    /** Alternative to billId — resolved within the caller's location. */
    @Size(max = 50)
    private String billNumber;

    /** Target agent. Omit when {@link #clearAgent} is true. */
    private Long agentId;

    @Size(max = 20)
    private String agentCommissionType;

    @PositiveOrZero
    private Double agentCommissionValue;

    @Size(max = 2000)
    private String agentCommissionNotes;

    /** When true, removes agent linkage (only allowed while commission is not PAID). */
    private Boolean clearAgent;
}
