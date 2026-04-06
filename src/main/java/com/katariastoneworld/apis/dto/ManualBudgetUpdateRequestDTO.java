package com.katariastoneworld.apis.dto;

import com.katariastoneworld.apis.entity.BudgetAdjustmentKind;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ManualBudgetUpdateRequestDTO {

    @NotNull
    private BudgetAdjustmentKind kind;

    /**
     * For {@link BudgetAdjustmentKind#ADD} / {@link BudgetAdjustmentKind#SUBTRACT}: positive delta magnitude.
     * For {@link BudgetAdjustmentKind#SET_BALANCE}: new absolute balance.
     */
    @NotNull
    private BigDecimal amount;

    private LocalDate budgetDate;
    private String note;
}
