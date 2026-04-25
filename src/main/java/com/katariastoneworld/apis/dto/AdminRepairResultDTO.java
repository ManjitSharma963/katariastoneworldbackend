package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminRepairResultDTO {
    private String operation;
    private String date;
    private String location;
    private Integer affectedRows;
    private String status;
}
