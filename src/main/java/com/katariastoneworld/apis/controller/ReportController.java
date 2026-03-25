package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.DailyClosingReportDTO;
import com.katariastoneworld.apis.service.DailyClosingReportService;
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
import java.util.Map;

@RestController
@RequestMapping({"/api/reports", "/reports"})
@Tag(name = "Reports", description = "Operational reports (daily closing, etc.)")
public class ReportController {

    @Autowired
    private DailyClosingReportService dailyClosingReportService;

    @Operation(summary = "Daily closing report",
            description = """
                    Location-scoped (from JWT). Pass **date** (start) and optional **dateTo** (end, inclusive).
                    When **dateTo** is omitted, the report is for the single day **date** (default closing behaviour).
                    When **date** and **dateTo** differ, bills with `bill_date` in the range are listed; **totalCollected** /
                    **paymentSummary** sum `bill_payments.payment_date` in that range; expenses use `expenses.date` in range;
                    **cashInHand** = cash collected in range − expenses in range.
                    **paymentSummary** keys include CASH, UPI, BANK_TRANSFER, CHEQUE, and OTHER (e.g. null mode).
                    Response may include **warnings** (e.g. long date range) and **collectionsReconciliationOk** / **collectionsReconciliationDelta**
                    to verify mode totals vs **totalCollected** (not a substitute for accounting sign-off).
                    **backfillLegacy** (default false): when `true` and the period is a single day, persists one `bill_payments` row
                    for legacy PAID bills that have `payment_method` but no payment rows (one-off data repair; not used by the inventory UI).
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
            @Parameter(description = """
                    When true and the period is a single day: writes one bill_payment row for legacy PAID bills that have
                    payment_method but no rows (use sparingly for DB repair; not exposed in the Reports UI). Default false.""")
            @RequestParam(defaultValue = "false") boolean backfillLegacy,
            HttpServletRequest request) {
        LocalDate end = dateTo != null ? dateTo : date;
        if (end.isBefore(date)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid date range",
                    "message", "dateTo must be on or after date"));
        }
        String location = RequestUtil.getLocationFromRequest(request);
        boolean backfill = backfillLegacy && date.equals(end);
        DailyClosingReportDTO dto = dailyClosingReportService.buildReportForPeriod(date, end, location, backfill);
        return ResponseEntity.ok(dto);
    }
}
