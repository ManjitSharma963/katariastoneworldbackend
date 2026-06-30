package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesAgentResponseDTO {

    private Long id;
    private String name;
    private String phone;
    private String email;
    private String defaultCommissionType;
    private Double defaultCommissionValue;
    private String notes;
    private String location;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Summary stats for list views */
    private Long totalDeals;
    private Double totalCommissionPending;
    private Double totalCommissionPaid;
}
