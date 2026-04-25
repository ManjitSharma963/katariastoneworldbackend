package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.DailyClosingReportDTO;
import com.katariastoneworld.apis.dto.DailyClosingSnapshotDTO;
import com.katariastoneworld.apis.dto.PaymentModeTotalsDTO;
import com.katariastoneworld.apis.dto.ReconciliationReportDTO;
import com.katariastoneworld.apis.dto.SalesChargesSummaryDTO;
import com.katariastoneworld.apis.service.DailyClosingReportService;
import com.katariastoneworld.apis.service.DailyClosingSnapshotService;
import com.katariastoneworld.apis.util.RequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/reports", "/reports"})
@Tag(name = "Reports", description = "Operational reports (daily closing, etc.)")
public class ReportController {

    @Autowired
    private DailyClosingReportService dailyClosingReportService;

    @Autowired
    private DailyClosingSnapshotService dailyClosingSnapshotService;

    @Operation(summary = "Daily closing report",
            description = """
                    Location-scoped (from JWT). Pass **date** (start) and optional **dateTo** (end, inclusive).
                    When **dateTo** is omitted, the report is for the single day **date** (default closing behaviour).
                    When **date** and **dateTo** differ, bills with `bill_date` in the range are listed; **totalCollected** /
                    **paymentSummary** sum `bill_payments.payment_date` in that range; expenses use `expenses.expense_date` in range;
                    **cashInHand** = cash collected in range − expenses in range.
                    **totalAdvanceDeposits** / **totalAdvanceAppliedOnBills** sum customer token deposits and advance usage
                    with `created_at` in the period (location via customer).
                    **paymentSummary** keys include CASH, UPI, BANK_TRANSFER, CHEQUE, and OTHER (e.g. null mode).
                    Response may include **warnings** (e.g. long date range) and **collectionsReconciliationOk** / **collectionsReconciliationDelta**
                    to verify mode totals vs **totalCollected** (not a substitute for accounting sign-off).
                    Physical table name is `bill_payments` (bill_kind + bill_id for GST vs non-GST).
                    """)
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DailyClosingReportDTO.class)))
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/daily-closing")
    @RequiresRole({"user", "admin"})
    public ResponseEntity<?> dailyClosing(
            @Parameter(description = "Start of period (inclusive), or the only day if dateTo is omitted", required = true, example = "2026-03-24")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "End of period (inclusive). Omit for a single-day report.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            HttpServletRequest request) {
        LocalDate end = dateTo != null ? dateTo : date;
        if (end.isBefore(date)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid date range",
                    "message", "dateTo must be on or after date"));
        }
        String location = RequestUtil.getLocationFromRequest(request);
        DailyClosingReportDTO dto = dailyClosingReportService.buildReportForPeriod(date, end, location);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/payment-mode-summary")
    @RequiresRole({"user", "admin"})
    public ResponseEntity<PaymentModeTotalsDTO> paymentModeSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        LocalDate end = dateTo != null ? dateTo : date;
        if (end.isBefore(date)) {
            end = date;
        }
        return ResponseEntity.ok(dailyClosingReportService.paymentModeTotalsForSales(date, end, location));
    }

    @Operation(summary = "Sales charge summary",
            description = "Date-based summary for sales sqft, labour charge and other-expense charge from bills.")
    @GetMapping("/sales-charges-summary")
    @RequiresRole({"user", "admin"})
    public ResponseEntity<SalesChargesSummaryDTO> salesChargesSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        LocalDate end = dateTo != null ? dateTo : date;
        if (end.isBefore(date)) {
            end = date;
        }
        return ResponseEntity.ok(dailyClosingReportService.salesChargesSummary(date, end, location));
    }

    @GetMapping("/reconciliation")
    @RequiresRole({"user", "admin"})
    public ResponseEntity<ReconciliationReportDTO> reconciliation(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(dailyClosingReportService.reconciliation(date, location));
    }

    @GetMapping("/daily-closing/snapshot")
    @RequiresRole({"user", "admin"})
    public ResponseEntity<DailyClosingSnapshotDTO> getSnapshot(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(dailyClosingSnapshotService.getSnapshot(date, location));
    }

    @GetMapping("/daily-closing/snapshots")
    @RequiresRole({"user", "admin"})
    public ResponseEntity<List<DailyClosingSnapshotDTO>> getSnapshotRange(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(dailyClosingSnapshotService.getSnapshots(from, to, location));
    }
}
