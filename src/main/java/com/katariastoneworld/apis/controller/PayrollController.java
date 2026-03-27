package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.EmployeePayrollSummaryDTO;
import com.katariastoneworld.apis.dto.PayrollAdvanceRequestDTO;
import com.katariastoneworld.apis.dto.PayrollSalarySettlementRequestDTO;
import com.katariastoneworld.apis.entity.EmployeePayrollLedgerEntry;
import com.katariastoneworld.apis.service.PayrollLedgerService;
import com.katariastoneworld.apis.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping(value = {"/api/payroll", "payroll"}, produces = "application/json")
@RequiresRole("admin")
public class PayrollController {

    @Autowired
    private PayrollLedgerService payrollLedgerService;

    @PostMapping("/employees/{employeeId}/advance")
    public ResponseEntity<EmployeePayrollLedgerEntry> recordAdvance(
            @PathVariable Long employeeId,
            @Valid @RequestBody PayrollAdvanceRequestDTO req,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        Long actor = RequestUtil.getUserIdFromRequest(request);
        EmployeePayrollLedgerEntry saved = payrollLedgerService.recordAdvance(employeeId, req, location, actor);
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

    @PostMapping("/employees/{employeeId}/salary-settlement")
    public ResponseEntity<EmployeePayrollSummaryDTO> settleSalary(
            @PathVariable Long employeeId,
            @Valid @RequestBody PayrollSalarySettlementRequestDTO req,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        Long actor = RequestUtil.getUserIdFromRequest(request);
        EmployeePayrollSummaryDTO summary = payrollLedgerService.settleSalaryMonth(employeeId, req, location, actor);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/employees/summary")
    public ResponseEntity<List<EmployeePayrollSummaryDTO>> monthlySummary(
            @RequestParam("month") String month,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(payrollLedgerService.getMonthlySummaries(location, month));
    }

    @GetMapping("/employees/{employeeId}/ledger")
    public ResponseEntity<List<EmployeePayrollLedgerEntry>> ledger(
            @PathVariable Long employeeId,
            @RequestParam(value = "from", required = false) LocalDate from,
            @RequestParam(value = "to", required = false) LocalDate to,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(payrollLedgerService.getLedger(employeeId, from, to, location));
    }
}

