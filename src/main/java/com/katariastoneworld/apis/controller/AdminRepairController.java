package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.AdminRepairResultDTO;
import com.katariastoneworld.apis.dto.DailyClosingSnapshotDTO;
import com.katariastoneworld.apis.service.DailyClosingReportService;
import com.katariastoneworld.apis.service.DailyClosingSnapshotService;
import com.katariastoneworld.apis.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Locale;

@RestController
@RequestMapping({"/api/admin/repair", "/admin/repair"})
@RequiresRole("admin")
public class AdminRepairController {

    @Autowired
    private DailyClosingReportService dailyClosingReportService;

    @Autowired
    private DailyClosingSnapshotService dailyClosingSnapshotService;

    @PostMapping("/reports/daily-closing/legacy-payments")
    public ResponseEntity<AdminRepairResultDTO> backfillLegacyPayments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest request) {
        LocalDate d = date != null ? date : LocalDate.now();
        String location = RequestUtil.getLocationFromRequest(request);
        int inserted = dailyClosingReportService.repairLegacyBillPaymentsForDate(d, location);
        return ResponseEntity.ok(AdminRepairResultDTO.builder()
                .operation("BACKFILL_LEGACY_BILL_PAYMENTS")
                .date(d.toString())
                .location(location)
                .affectedRows(inserted)
                .status("SUCCESS")
                .build());
    }

    @PostMapping("/reports/daily-closing/snapshot")
    public ResponseEntity<DailyClosingSnapshotDTO> repairSnapshot(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(dailyClosingSnapshotService.createOrUpdateSnapshot(date, location));
    }

    @PostMapping("/reconciliation/resolve")
    public ResponseEntity<AdminRepairResultDTO> resolveReconciliationIssue(
            @RequestParam String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest request) {
        LocalDate d = date != null ? date : LocalDate.now();
        String location = RequestUtil.getLocationFromRequest(request);
        String normalizedAction = action.trim().toUpperCase(Locale.ROOT);

        if ("BACKFILL_MISSING_PAYMENT_ENTRIES".equals(normalizedAction)) {
            int affected = dailyClosingReportService.repairLegacyBillPaymentsForDate(d, location);
            return ResponseEntity.ok(AdminRepairResultDTO.builder()
                    .operation(normalizedAction)
                    .date(d.toString())
                    .location(location)
                    .affectedRows(affected)
                    .status("SUCCESS")
                    .build());
        }

        if ("REGENERATE_DAILY_SNAPSHOT".equals(normalizedAction)) {
            dailyClosingSnapshotService.createOrUpdateSnapshot(d, location);
            return ResponseEntity.ok(AdminRepairResultDTO.builder()
                    .operation(normalizedAction)
                    .date(d.toString())
                    .location(location)
                    .affectedRows(1)
                    .status("SUCCESS")
                    .build());
        }

        throw new IllegalArgumentException(
                "Unsupported action. Use BACKFILL_MISSING_PAYMENT_ENTRIES or REGENERATE_DAILY_SNAPSHOT");
    }
}
