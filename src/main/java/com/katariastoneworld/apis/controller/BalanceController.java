package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.BalanceSummaryDTO;
import com.katariastoneworld.apis.dto.CashBankTransferHistoryDTO;
import com.katariastoneworld.apis.dto.CashBankTransferRequestDTO;
import com.katariastoneworld.apis.dto.UnifiedLedgerTransactionDTO;
import com.katariastoneworld.apis.service.BalanceSummaryService;
import com.katariastoneworld.apis.service.CashBankTransferService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/balance", produces = "application/json")
@RequiresRole("admin")
@Tag(name = "Balance", description = "Cash/bank net balances from the transactions ledger.")
public class BalanceController {

    @Autowired
    private BalanceSummaryService balanceSummaryService;

    @Autowired
    private CashBankTransferService cashBankTransferService;

    @Operation(summary = "Cash and bank balance summary",
            description = "In-hand = net CREDIT−DEBIT for CASH+UPI; bank = net for BANK+CARD+CHEQUE. "
                    + "Also returns todayDebitCashUpi / todayDebitBank: sums of DEBIT amounts today per rail "
                    + "(expenses, client purchase payments, payroll, etc.).")
    @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = BalanceSummaryDTO.class)))
    @GetMapping("/summary")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<BalanceSummaryDTO> getSummary(HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(balanceSummaryService.getSummary(location));
    }

    @Operation(summary = "Transaction history", description = "Rows from transactions for the location, newest first.")
    @ApiResponse(responseCode = "200", description = "Success")
    @GetMapping("/transactions")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<UnifiedLedgerTransactionDTO>> listTransactions(
            @Parameter(description = "Inclusive start (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Inclusive end (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "200") int limit,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(balanceSummaryService.listTransactions(location, from, to, limit));
    }

    @Operation(summary = "Cash ↔ bank transfer history",
            description = "Grouped transfer records (one row per cash/bank move), newest first.")
    @ApiResponse(responseCode = "200", description = "Success")
    @GetMapping("/transfers")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<CashBankTransferHistoryDTO>> listTransfers(
            @Parameter(description = "Inclusive start (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Inclusive end (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "100") int limit,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(cashBankTransferService.listHistory(location, from, to, limit));
    }

    @Operation(summary = "Transfer between cash and bank",
            description = "Moves money between CASH+UPI and BANK rails without changing total liquidity. "
                    + "CASH_TO_BANK deducts cash+UPI and credits bank; BANK_TO_CASH does the reverse.")
    @ApiResponse(responseCode = "200", description = "Transfer recorded; returns updated balance summary",
            content = @Content(schema = @Schema(implementation = BalanceSummaryDTO.class)))
    @PostMapping("/transfer")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<BalanceSummaryDTO> transferCashBank(
            @Valid @RequestBody CashBankTransferRequestDTO body,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(cashBankTransferService.transfer(location, body));
    }
}
