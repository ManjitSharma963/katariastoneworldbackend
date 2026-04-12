package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.DailyBudgetRequestDTO;
import com.katariastoneworld.apis.dto.DailyBudgetStatusDTO;
import com.katariastoneworld.apis.dto.DailyBudgetSummaryDTO;
import com.katariastoneworld.apis.dto.DailyBudgetEventDTO;
import com.katariastoneworld.apis.dto.DailyBudgetCalculatedSummaryDTO;
import com.katariastoneworld.apis.dto.InHandReconciliationDTO;
import com.katariastoneworld.apis.dto.LoanReceiptRequestDTO;
import com.katariastoneworld.apis.service.DailyBudgetService;
import com.katariastoneworld.apis.service.InHandReconciliationService;
import com.katariastoneworld.apis.service.LoanLedgerService;
import com.katariastoneworld.apis.util.RequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.LocalDate;

@RestController
@RequestMapping(value = {"/api/budget/daily", "/api/daily-budget", "/budget/daily"}, produces = "application/json")
@RequiresRole("admin")
@Tag(name = "Daily Budget", description = "Daily budget: set a budget amount; daily expenses are deducted from it for the day.")
public class DailyBudgetController {

    @Autowired
    private DailyBudgetService dailyBudgetService;

    @Autowired
    private InHandReconciliationService inHandReconciliationService;

    @Autowired
    private LoanLedgerService loanLedgerService;

    @Operation(summary = "Get all budgets", description = "Returns all rows from the daily_budget table (all locations).")
    @ApiResponse(responseCode = "200", description = "Success")
    @GetMapping("/all")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<DailyBudgetSummaryDTO>> getAllBudgets() {
        return ResponseEntity.ok(dailyBudgetService.getAllBudgets());
    }

    @Operation(summary = "Get daily budget status", description = "Returns budget amount, today's daily expenses total (spent), and remaining. Location from JWT.")
    @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = DailyBudgetStatusDTO.class)))
    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<DailyBudgetStatusDTO> getBudgetStatus(HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        DailyBudgetStatusDTO status = dailyBudgetService.getBudgetStatus(location);
        return ResponseEntity.ok(status);
    }

    @Operation(summary = "Get daily budget status for a date", description = "Same as GET but for a specific date. Useful for history.")
    @Parameter(name = "date", description = "Date in yyyy-MM-dd", example = "2026-02-16")
    @GetMapping("/by-date")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<DailyBudgetStatusDTO> getBudgetStatusForDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        DailyBudgetStatusDTO status = dailyBudgetService.getBudgetStatus(location, date);
        return ResponseEntity.ok(status);
    }

    @Operation(summary = "Record cash borrowed (loan draw)",
            description = "Increases today's in-hand budget like a cash collection. Repayments should be entered as a daily expense "
                    + "(category loan_repayment) so they debit the budget and appear in expenses.")
    @PostMapping("/loan-receipt")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<DailyBudgetStatusDTO> recordLoanReceipt(
            @Valid @RequestBody LoanReceiptRequestDTO body,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        loanLedgerService.recordLoanReceipt(location, body);
        return ResponseEntity.ok(dailyBudgetService.getBudgetStatus(location));
    }

    @Operation(summary = "Calculated summary for date range",
            description = "Remaining balance (as-of min(to, today)) and sum of EXPENSE_DEBIT/EXPENSE_CREDIT from daily_budget_events in [from, to]. "
                    + "Computed on the server; avoids client-side replay of capped history lists.")
    @GetMapping("/summary")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<DailyBudgetCalculatedSummaryDTO> getCalculatedSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(dailyBudgetService.getCalculatedSummary(location, from, to));
    }

    @Operation(summary = "Set daily budget", description = "Set or update the daily budget amount for your location. Daily expenses (type=daily) are deducted from this for each day.")
    @ApiResponse(responseCode = "200", description = "Budget updated", content = @Content(schema = @Schema(implementation = DailyBudgetStatusDTO.class)))
    @PutMapping
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<DailyBudgetStatusDTO> setBudget(
            @Valid @RequestBody DailyBudgetRequestDTO requestDTO,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        DailyBudgetStatusDTO status = dailyBudgetService.setBudget(location, requestDTO);
        return ResponseEntity.ok(status);
    }

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<DailyBudgetStatusDTO> setBudgetPost(
            @Valid @RequestBody DailyBudgetRequestDTO requestDTO,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        DailyBudgetStatusDTO status = dailyBudgetService.setBudget(location, requestDTO);
        return ResponseEntity.ok(status);
    }

    @Operation(summary = "Delete daily budget", description = "Remove the daily budget for your location. Returns 204 if deleted, 404 if no budget was set.")
    @ApiResponse(responseCode = "204", description = "Budget deleted")
    @ApiResponse(responseCode = "404", description = "No budget found for location")
    @DeleteMapping
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> deleteBudget(HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        boolean deleted = dailyBudgetService.deleteBudget(location);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @Operation(summary = "Daily budget event history",
            description = "Returns opening/closing balances for daily budget for the authenticated user's location. Each event is recorded when the daily budget is set/changed or when daily budget remaining is adjusted due to today's expenses/roll-over.")
    @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = DailyBudgetEventDTO.class)))
    @GetMapping("/history")
    public ResponseEntity<List<DailyBudgetEventDTO>> getBudgetEvents(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "50") Integer limit,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        int lim = limit != null ? Math.max(1, Math.min(500, limit)) : 50;
        return ResponseEntity.ok(dailyBudgetService.getBudgetEvents(location, from, to, lim));
    }

    @Operation(summary = "Reconcile CASH/UPI bill payments vs ledger in-hand",
            description = "Compares totals from bill_payments (CASH+UPI, active bills) to financial_ledger BILL_PAYMENT in_hand for the same location and date range. "
                    + "daily_budget_events are append-only: a deleted bill adds a reversing event; older collection rows remain for audit.")
    @GetMapping("/reconcile-in-hand")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<InHandReconciliationDTO> reconcileInHand(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(inHandReconciliationService.reconcileInHandBillPayments(location, from, to));
    }
}
