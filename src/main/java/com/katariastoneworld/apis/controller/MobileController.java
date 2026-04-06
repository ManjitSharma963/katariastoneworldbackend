package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.MobileDashboardDTO;
import com.katariastoneworld.apis.service.MobileDashboardService;
import com.katariastoneworld.apis.util.RequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Lightweight JSON for PWA / future React Native clients.
 * CORS for browser/PWA is handled globally ({@code SimpleCorsFilter} + {@code cors.allow-all-origins} / allowed list in
 * {@code application.properties}); do not duplicate with a second {@code WebMvcConfigurer} bean.
 */
@RestController
@RequestMapping("/api/mobile")
@Tag(name = "Mobile", description = "Compact APIs for PWA / mobile clients")
public class MobileController {

    private final MobileDashboardService mobileDashboardService;

    public MobileController(MobileDashboardService mobileDashboardService) {
        this.mobileDashboardService = mobileDashboardService;
    }

    @Operation(summary = "Today’s ledger dashboard",
            description = """
                    Location from JWT. **totalSales** = sum of CREDIT {@code amount}; **totalExpense** = sum of DEBIT;
                    **netBalance** = totalSales − totalExpense; **paymentModes** = CREDIT sums grouped by {@code payment_mode}.
                    All from {@code financial_ledger} with {@code is_deleted = 0} for **event_date** (default: today).
                    Optional **date** overrides the calendar day (inclusive).
                    """)
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = MobileDashboardDTO.class)))
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/dashboard")
    @RequiresRole({"user", "admin"})
    public ResponseEntity<MobileDashboardDTO> dashboard(
            @Parameter(description = "Calendar day (ISO). Default: today (server local date).")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        LocalDate day = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(mobileDashboardService.buildForDate(location, day));
    }
}
