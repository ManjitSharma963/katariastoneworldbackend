package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.LoanLedgerEntryResponseDTO;
import com.katariastoneworld.apis.dto.LoanLenderSummaryDTO;
import com.katariastoneworld.apis.service.LoanLedgerService;
import com.katariastoneworld.apis.util.RequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = {"/api/loans", "/loans"}, produces = "application/json")
@RequiresRole("admin")
@Tag(name = "Loans", description = "Market / financier borrowing: lenders and per-lender ledger.")
public class LoanController {

    @Autowired
    private LoanLedgerService loanLedgerService;

    @Operation(summary = "List lenders with borrowed / repaid / outstanding totals")
    @GetMapping("/lenders")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<LoanLenderSummaryDTO>> listLenders(HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(loanLedgerService.listLenderSummaries(location));
    }

    @Operation(summary = "Full transaction history for one lender")
    @GetMapping("/lenders/{lenderId}/ledger")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<LoanLedgerEntryResponseDTO>> lenderLedger(
            @PathVariable Long lenderId,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(loanLedgerService.listLedgerForLender(location, lenderId));
    }
}
