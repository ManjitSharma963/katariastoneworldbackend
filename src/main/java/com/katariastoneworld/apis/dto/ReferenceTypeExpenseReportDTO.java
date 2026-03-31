package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/** Category-style breakdown: DEBIT totals grouped by {@code reference_type}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferenceTypeExpenseReportDTO {

    private String location;
    private LocalDate date;
    private LocalDate dateTo;
    private List<ReferenceTypeExpenseRowDTO> byReferenceType;
}
