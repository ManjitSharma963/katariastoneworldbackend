package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.*;
import com.katariastoneworld.apis.entity.MoneyTxType;
import com.katariastoneworld.apis.service.UnifiedCashbookService;
import com.katariastoneworld.apis.util.RequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping(value = { "/api/cashbook", "/cashbook" }, produces = "application/json")
@RequiresRole("admin")
@Tag(name = "Unified cashbook", description = "Single ledger for income/expense + day budget")
public class UnifiedCashbookController {

    private final UnifiedCashbookService unifiedCashbookService;

    public UnifiedCashbookController(UnifiedCashbookService unifiedCashbookService) {
        this.unifiedCashbookService = unifiedCashbookService;
    }

    @Operation(summary = "Create transaction (income or expense)")
    @PostMapping(value = "/transactions", consumes = "application/json")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MoneyTransactionResponseDTO> postTransaction(
            @Valid @RequestBody MoneyTransactionRequestDTO body,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        Long userId = RequestUtil.getUserIdFromRequestOptional(request).orElse(null);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(unifiedCashbookService.createTransaction(body, location, userId));
    }

    @Operation(summary = "Update transaction")
    @PutMapping(value = "/transactions/{id}", consumes = "application/json")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MoneyTransactionResponseDTO> putTransaction(@PathVariable Long id,
            @Valid @RequestBody MoneyTransactionRequestDTO body,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(unifiedCashbookService.updateTransaction(id, body, location));
    }

    @Operation(summary = "Soft-delete transaction and rebuild day balance")
    @DeleteMapping("/transactions/{id}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> deleteTransaction(@PathVariable Long id, HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        unifiedCashbookService.deleteTransaction(id, location);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List cashbook rows (transactions + manual adjustments)")
    @GetMapping("/transactions")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CashbookListResponseDTO> listTransactions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) MoneyTxType type,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(unifiedCashbookService.listCashbook(location, from, to, type));
    }

    @Operation(summary = "Today's budget snapshot for JWT location")
    @GetMapping("/budget/today")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<TodayBudgetDTO> budgetToday(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(unifiedCashbookService.getTodayBalance(location, date));
    }

    @Operation(summary = "Manual budget adjustment (add / subtract / set balance)")
    @PostMapping(value = "/budget/update", consumes = "application/json")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<TodayBudgetDTO> budgetUpdate(@Valid @RequestBody ManualBudgetUpdateRequestDTO body,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        Long userId = RequestUtil.getUserIdFromRequestOptional(request).orElse(null);
        return ResponseEntity.ok(unifiedCashbookService.updateBudgetManually(body, location, userId));
    }
}
