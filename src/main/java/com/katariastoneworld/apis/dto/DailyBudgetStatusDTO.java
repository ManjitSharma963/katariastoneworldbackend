package com.katariastoneworld.apis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DailyBudgetStatusDTO {

    /** Daily budget amount set for this location */
    private BigDecimal budgetAmount;

    /** Total daily expenses for the date (default: today) */
    private BigDecimal spentAmount;

    /** budgetAmount - spentAmount (can be negative if overspent) */
    private BigDecimal remainingAmount;

    /** Date for which spent is calculated (default: today) */
    private LocalDate date;

    private String location;
}
