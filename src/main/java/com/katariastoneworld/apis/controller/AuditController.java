package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.LedgerAuditEntryDTO;
import com.katariastoneworld.apis.entity.BillKind;
import com.katariastoneworld.apis.service.AuditLedgerService;
import com.katariastoneworld.apis.util.RequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/audit")
@Tag(name = "Audit", description = "Ledger audit trail (admin only)")
@RequiresRole("admin")
@SecurityRequirement(name = "bearerAuth")
public class AuditController {

    @Autowired
    private AuditLedgerService auditLedgerService;

    @Operation(summary = "Ledger audit feed",
            description = """
                    Location from JWT. Includes **active and soft-deleted** rows. Optional filters: **date** (event_date),
                    **source** (source_type, e.g. BILL_PAYMENT), **userId** (created_by). **limit** default 100, max 500.
                    Ordered by created_at descending.
                    """)
    @GetMapping("/ledger")
    public ResponseEntity<List<LedgerAuditEntryDTO>> ledgerAudit(
            @Parameter(description = "Filter by ledger event_date")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "Filter by source_type (e.g. BILL_PAYMENT, EXPENSE)")
            @RequestParam(required = false) String source,
            @Parameter(description = "Filter by created_by user id")
            @RequestParam(required = false) Long userId,
            @Parameter(description = "Max rows (default 100, max 500)")
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(auditLedgerService.listLedgerAudit(location, date, source, userId,
                limit != null ? limit : 100));
    }

    @Operation(summary = "Bill-scoped ledger rows",
            description = """
                    Rows with reference_type BILL and reference_id = bill id, scoped to JWT location.
                    **billKind** (GST or NON_GST) recommended so GST and non-GST bills with the same numeric id do not mix.
                    """)
    @GetMapping("/bill/{billId}")
    public ResponseEntity<List<LedgerAuditEntryDTO>> billLedger(
            @PathVariable Long billId,
            @RequestParam(required = false) BillKind billKind,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(auditLedgerService.listBillLedger(location, billId, billKind));
    }
}
