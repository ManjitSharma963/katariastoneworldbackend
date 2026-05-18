package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.*;
import com.katariastoneworld.apis.service.BillAdjustmentService;
import com.katariastoneworld.apis.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({ "/api/bills", "/bills" })
public class BillAdjustmentController {

    private final BillAdjustmentService billAdjustmentService;

    public BillAdjustmentController(BillAdjustmentService billAdjustmentService) {
        this.billAdjustmentService = billAdjustmentService;
    }

    @PostMapping("/{billType}/{id}/adjustment-session")
    @RequiresRole({ "user", "admin" })
    public ResponseEntity<BillAdjustmentSessionDTO> openAdjustmentSession(
            @PathVariable String billType,
            @PathVariable Long id,
            HttpServletRequest request) {
        billAdjustmentService.assertNonGstBillType(billType);
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(billAdjustmentService.openAdjustmentSession(id, location));
    }

    @PostMapping("/{billType}/{id}/returns")
    @RequiresRole({ "user", "admin" })
    public ResponseEntity<BillStockReturnResponseDTO> createReturn(
            @PathVariable String billType,
            @PathVariable Long id,
            @Valid @RequestBody BillReturnCreateApiDTO body,
            HttpServletRequest request) {
        billAdjustmentService.assertNonGstBillType(billType);
        String location = RequestUtil.getLocationFromRequest(request);
        Long userId = RequestUtil.getUserIdFromRequest(request);
        BillStockReturnRequestDTO mapped = body != null ? body.toStockReturnRequest() : new BillStockReturnRequestDTO();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(billAdjustmentService.recordReturn(id, mapped, location, userId));
    }

    @PostMapping("/{billType}/{id}/finalize-adjustment")
    @RequiresRole({ "user", "admin" })
    public ResponseEntity<BillFinalizeAdjustmentResponseDTO> finalizeAdjustment(
            @PathVariable String billType,
            @PathVariable Long id,
            @Valid @RequestBody BillFinalizeAdjustmentRequestDTO body,
            HttpServletRequest request) {
        billAdjustmentService.assertNonGstBillType(billType);
        String location = RequestUtil.getLocationFromRequest(request);
        Long userId = RequestUtil.getUserIdFromRequest(request);
        String role = RequestUtil.getRoleFromRequest(request);
        return ResponseEntity.ok(billAdjustmentService.finalizeAdjustment(id, body, location, userId, role));
    }

    @GetMapping("/{billType}/{id}/adjustments")
    @RequiresRole({ "user", "admin" })
    public ResponseEntity<BillAdjustmentHistoryDTO> listAdjustments(
            @PathVariable String billType,
            @PathVariable Long id,
            HttpServletRequest request) {
        billAdjustmentService.assertNonGstBillType(billType);
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(billAdjustmentService.getAdjustmentHistory(id, location));
    }
}
