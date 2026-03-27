package com.katariastoneworld.apis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmployeePayrollSummaryDTO {
    private Long employeeId;
    private String employeeName;
    private String month; // YYYY-MM

    private Double salaryDue;
    private Double advanceBalanceStart;
    private Double advancesGivenInMonth;
    private Double advancesAppliedInMonth;
    private Double salaryCashPaidInMonth;

    /**
     * Net salary still payable for this month (after advance pool and ledger settlement).
     * Negative means advance taken exceeds this month's salary; excess is carried in advanceBalanceEnd into the next month.
     */
    private Double salaryRemaining;
    private Double advanceBalanceEnd;

    /** PAID | PARTIAL | OVER_ADVANCE (advance pool exceeds salary still due before formal settlement). */
    private String status;
}

