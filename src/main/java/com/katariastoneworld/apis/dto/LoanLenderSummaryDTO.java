package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoanLenderSummaryDTO {
    private Long id;
    private String displayName;
    private BigDecimal totalBorrowed;
    private BigDecimal totalRepaid;
    private BigDecimal outstanding;
}
