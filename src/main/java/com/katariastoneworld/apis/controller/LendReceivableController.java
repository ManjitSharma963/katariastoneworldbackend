package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.DailyBudgetStatusDTO;
import com.katariastoneworld.apis.dto.ReceivableBorrowerSummaryDTO;
import com.katariastoneworld.apis.dto.ReceivableLendRequestDTO;
import com.katariastoneworld.apis.dto.ReceivableLedgerEntryResponseDTO;
import com.katariastoneworld.apis.service.DailyBudgetService;
import com.katariastoneworld.apis.service.ReceivableLedgerService;
import com.katariastoneworld.apis.util.RequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = {"/api/loans/lend", "/loans/lend"}, produces = "application/json")
@RequiresRole("admin")
@Tag(name = "Lend / receivable", description = "Money lent to others: borrowers, disbursements, repayments received.")
public class LendReceivableController {

    @Autowired
    private ReceivableLedgerService receivableLedgerService;

    @Autowired
    private DailyBudgetService dailyBudgetService;

    @Operation(summary = "List borrowers with total lent / collected / outstanding")
    @GetMapping("/borrowers")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<ReceivableBorrowerSummaryDTO>> listBorrowers(HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(receivableLedgerService.listBorrowerSummaries(location));
    }

    @Operation(summary = "Ledger for one borrower (disbursements and repayments received)")
    @GetMapping("/borrowers/{borrowerId}/ledger")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<ReceivableLedgerEntryResponseDTO>> borrowerLedger(
            @PathVariable Long borrowerId,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(receivableLedgerService.listLedgerForBorrower(location, borrowerId));
    }

    @Operation(summary = "Record money lent out (disbursement)",
            description = "DEBIT in unified ledger. Cash/UPI reduce today's in-hand daily budget.")
    @PostMapping("/disbursements")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<DailyBudgetStatusDTO> recordDisbursement(
            @Valid @RequestBody ReceivableLendRequestDTO body,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        receivableLedgerService.recordDisbursement(location, body);
        return ResponseEntity.ok(dailyBudgetService.getBudgetStatus(location));
    }

    @Operation(summary = "Record repayment received from a borrower",
            description = "CREDIT in unified ledger. Cash/UPI increase today's in-hand daily budget.")
    @PostMapping("/repayments")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<DailyBudgetStatusDTO> recordRepayment(
            @Valid @RequestBody ReceivableLendRequestDTO body,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        receivableLedgerService.recordRepaymentReceived(location, body);
        return ResponseEntity.ok(dailyBudgetService.getBudgetStatus(location));
    }
}
