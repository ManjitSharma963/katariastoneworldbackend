package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.LoanLedgerEntryResponseDTO;
import com.katariastoneworld.apis.dto.LoanTransactionEditRequestDTO;
import com.katariastoneworld.apis.dto.ReceivableLedgerEntryResponseDTO;
import com.katariastoneworld.apis.service.LoanLedgerService;
import com.katariastoneworld.apis.service.ReceivableLedgerService;
import com.katariastoneworld.apis.util.RequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = {"/api/loans/transactions", "/loans/transactions"}, produces = "application/json")
@RequiresRole("admin")
@Tag(name = "Loan transactions", description = "Edit or delete Give/Take rows from the last 7 days.")
public class LoanTransactionController {

    @Autowired
    private LoanLedgerService loanLedgerService;

    @Autowired
    private ReceivableLedgerService receivableLedgerService;

    @Operation(summary = "Update a lender-side loan row (borrow / repay)")
    @PutMapping("/lender/{entryId}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<LoanLedgerEntryResponseDTO> updateLenderEntry(
            @PathVariable Long entryId,
            @Valid @RequestBody LoanTransactionEditRequestDTO body,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(loanLedgerService.updateEntry(location, entryId, body));
    }

    @Operation(summary = "Delete a lender-side loan row")
    @DeleteMapping("/lender/{entryId}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> deleteLenderEntry(
            @PathVariable Long entryId,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        loanLedgerService.deleteEntry(location, entryId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Update a borrower-side loan row (lend / collect)")
    @PutMapping("/borrower/{entryId}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ReceivableLedgerEntryResponseDTO> updateBorrowerEntry(
            @PathVariable Long entryId,
            @Valid @RequestBody LoanTransactionEditRequestDTO body,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(receivableLedgerService.updateEntry(location, entryId, body));
    }

    @Operation(summary = "Delete a borrower-side loan row")
    @DeleteMapping("/borrower/{entryId}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> deleteBorrowerEntry(
            @PathVariable Long entryId,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        receivableLedgerService.deleteEntry(location, entryId);
        return ResponseEntity.noContent().build();
    }
}
