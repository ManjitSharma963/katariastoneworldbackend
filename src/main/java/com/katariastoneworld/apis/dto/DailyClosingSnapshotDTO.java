package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyClosingSnapshotDTO {
    private Long id;
    private String location;
    private LocalDate snapshotDate;
    private Double totalSales;
    private Double totalExpenses;
    private Double totalCollections;
    private Double inHand;
    private String reconciliationStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
