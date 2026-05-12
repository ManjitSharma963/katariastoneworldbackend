package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.BillCancelRequestDTO;
import com.katariastoneworld.apis.dto.BillLineQuantitiesPatchRequestDTO;
import com.katariastoneworld.apis.dto.BillRequestDTO;
import com.katariastoneworld.apis.dto.BillResponseDTO;
import com.katariastoneworld.apis.dto.BillStockReturnRequestDTO;
import com.katariastoneworld.apis.dto.BillStockReturnResponseDTO;
import com.katariastoneworld.apis.dto.BillCancellationLogDTO;
import com.katariastoneworld.apis.dto.BillPaymentRequestDTO;
import com.katariastoneworld.apis.service.BillService;
import com.katariastoneworld.apis.service.EmailService;
import com.katariastoneworld.apis.service.PdfService;
import com.katariastoneworld.apis.util.RequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping({ "/api/bills", "/bills" })
@Tag(name = "Bills", description = "Bill management endpoints for GST and Non-GST bills")
public class BillController {
    private static final Logger log = LoggerFactory.getLogger(BillController.class);

    @Autowired
    private BillService billService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PdfService pdfService;

    @Operation(summary = "Create a new bill", description = "Create a new bill (GST or Non-GST) with customer and items. Location is automatically extracted from JWT token.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Bill created successfully", content = @Content(schema = @Schema(implementation = BillResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @RequiresRole({ "user", "admin" })
    public ResponseEntity<BillResponseDTO> createBill(@Valid @RequestBody BillRequestDTO billRequestDTO,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        Long userId = RequestUtil.getUserIdFromRequest(request);
        String role = RequestUtil.getRoleFromRequest(request);
        log.info("Creating bill request for location={} userId={}", location, userId);
        BillResponseDTO response = billService.createBill(billRequestDTO, location, userId, role);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/{billType}/{id}/supplementary")
    @RequiresRole({ "user", "admin" })
    public ResponseEntity<BillResponseDTO> createSupplementaryBill(
            @PathVariable String billType,
            @PathVariable Long id,
            @Valid @RequestBody BillRequestDTO billRequestDTO,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        Long userId = RequestUtil.getUserIdFromRequest(request);
        String role = RequestUtil.getRoleFromRequest(request);
        BillResponseDTO response = billService.createSupplementaryBill(id, billType, billRequestDTO, location, userId, role);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(summary = "Get bill by bill number", description = "Retrieve a bill by its unique bill number")
    @Parameter(name = "billNumber", description = "Bill number (sequential number: 1, 2, 3, etc.)", required = true)
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @GetMapping("/number/{billNumber}")
    @RequiresRole("admin")
    public ResponseEntity<BillResponseDTO> getBillByBillNumber(@PathVariable String billNumber,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        BillResponseDTO response = billService.getBillByBillNumber(billNumber, location);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{billType}/{id}")
    @RequiresRole("admin")
    public ResponseEntity<BillResponseDTO> getBillById(@PathVariable String billType, @PathVariable Long id,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        BillResponseDTO response = billService.getBillById(id, billType, location);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @RequiresRole("admin")
    public ResponseEntity<BillResponseDTO> getBillByIdSimple(@PathVariable Long id, HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        // Try to find in GST bills first, then Non-GST
        try {
            BillResponseDTO response = billService.getBillById(id, "gst", location);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            try {
                BillResponseDTO response = billService.getBillById(id, "nongst", location);
                return ResponseEntity.ok(response);
            } catch (Exception ex) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
        }
    }

    @Operation(summary = "Get all bills", description = "Get all bills for the authenticated user's location. Optional query param createdBy=userId to get only bills created by that user. Requires admin role.")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @GetMapping
    @RequiresRole("admin")
    public ResponseEntity<List<BillResponseDTO>> getAllBills(
            @RequestParam(required = false) Long createdBy,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        // By default show all bills for this location (admin + workers). Use ?createdBy=userId to filter by who created.
        List<BillResponseDTO> bills = billService.getAllBills(location, createdBy);
        return ResponseEntity.ok(bills);
    }

    @Operation(summary = "Get all sales", description = "All bills (GST + non-GST) for your location, same as GET /api/bills. Each item includes paymentMethod and paymentMode (same value) for sale tables.")
    @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = BillResponseDTO.class)))
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @GetMapping("/sales")
    @RequiresRole("admin")
    public ResponseEntity<List<BillResponseDTO>> getAllSales(HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        List<BillResponseDTO> sales = billService.getAllSales(location);
        return ResponseEntity.ok(sales);
    }

    @Operation(summary = "Cancelled bills (audit)",
            description = "Immutable log of bills cancelled via delete, for the branch. Filtered by bill date (inclusive). "
                    + "Defaults: last 30 days through today if params omitted.")
    @GetMapping("/cancellations")
    @RequiresRole({ "user", "admin" })
    public ResponseEntity<List<BillCancellationLogDTO>> listBillCancellations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate billDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate billDateTo,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(billService.getBillCancellationLogs(location, billDateFrom, billDateTo));
    }

    @GetMapping("/customer/{mobileNumber}")
    @RequiresRole("admin")
    public ResponseEntity<List<BillResponseDTO>> getBillsByMobileNumber(@PathVariable String mobileNumber,
            HttpServletRequest request) {
        if (mobileNumber == null || !mobileNumber.matches("^[0-9]{10}$")) {
            return ResponseEntity.badRequest().build();
        }
        String location = RequestUtil.getLocationFromRequest(request);
        List<BillResponseDTO> bills = billService.getBillsByMobileNumber(mobileNumber, location);
        return ResponseEntity.ok(bills);
    }

    @PostMapping("/test-email")
    @RequiresRole("admin")
    public ResponseEntity<String> sendTestEmail(
            @RequestParam(required = false, defaultValue = "manjitsharma963@gmail.com") String email) {
        try {
            emailService.sendTestEmail(email);
            return ResponseEntity.ok("Test email sent successfully to: " + email);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to send test email: " + e.getMessage());
        }
    }

    /**
     * Download bill PDF by bill number
     * GET /api/bills/number/{billNumber}/download
     */
    @Operation(summary = "Download bill PDF by bill number", description = "Download the PDF version of a bill by its bill number")
    @Parameter(name = "billNumber", description = "Bill number", required = true)
    @ApiResponse(responseCode = "200", description = "PDF file downloaded successfully", content = @Content(mediaType = "application/pdf"))
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @GetMapping("/number/{billNumber}/download")
    @RequiresRole("admin")
    public ResponseEntity<byte[]> downloadBillByNumber(@PathVariable String billNumber, HttpServletRequest request) {
        try {
            String location = RequestUtil.getLocationFromRequest(request);
            BillResponseDTO bill = billService.getBillByBillNumber(billNumber, location);
            log.info(
                    "Generating PDF for bill number={} subtotal={} totalAmount={} labour={} transportation={} otherExpenses={}",
                    billNumber, bill.getSubtotal(), bill.getTotalAmount(), bill.getLabourCharge(),
                    bill.getTransportationCharge(), bill.getOtherExpenses());
            byte[] pdfBytes = generatePdfBytes(bill);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "Bill_" + billNumber + ".pdf");
            headers.setContentLength(pdfBytes.length);

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Download GST bill PDF by ID
     * GET /api/bills/gst/{id}/download
     */
    @GetMapping("/gst/{id}/download")
    @RequiresRole("admin")
    public ResponseEntity<byte[]> downloadGSTBillById(@PathVariable Long id, HttpServletRequest request) {
        try {
            String location = RequestUtil.getLocationFromRequest(request);
            BillResponseDTO bill = billService.getBillById(id, "gst", location);
            byte[] pdfBytes = generatePdfBytes(bill);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "Bill_" + bill.getBillNumber() + ".pdf");
            headers.setContentLength(pdfBytes.length);

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            log.error("Error downloading GST bill with id={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Download Non-GST bill PDF by ID
     * GET /api/bills/nongst/{id}/download
     */
    @GetMapping("/nongst/{id}/download")
    @RequiresRole("admin")
    public ResponseEntity<byte[]> downloadNonGSTBillById(@PathVariable Long id, HttpServletRequest request) {
        try {
            String location = RequestUtil.getLocationFromRequest(request);
            BillResponseDTO bill = billService.getBillById(id, "nongst", location);
            byte[] pdfBytes = generatePdfBytes(bill);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "Bill_" + bill.getBillNumber() + ".pdf");
            headers.setContentLength(pdfBytes.length);

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Download bill PDF by ID and type (gst or nongst) - Generic endpoint
     * GET /api/bills/{billType}/{id}/download
     */
    @GetMapping("/{billType}/{id}/download")
    @RequiresRole("admin")
    public ResponseEntity<byte[]> downloadBillById(@PathVariable String billType, @PathVariable Long id,
            HttpServletRequest request) {
        try {
            String location = RequestUtil.getLocationFromRequest(request);
            BillResponseDTO bill = billService.getBillById(id, billType, location);
            byte[] pdfBytes = generatePdfBytes(bill);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "Bill_" + bill.getBillNumber() + ".pdf");
            headers.setContentLength(pdfBytes.length);

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{billType}/{id}/payments")
    @RequiresRole({ "user", "admin" })
    public ResponseEntity<BillResponseDTO> addPaymentToExistingBill(
            @PathVariable String billType,
            @PathVariable Long id,
            @Valid @RequestBody BillPaymentRequestDTO paymentRequest,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        Long userId = RequestUtil.getUserIdFromRequest(request);
        BillResponseDTO response = billService.addPaymentToBill(id, billType, paymentRequest, location, userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{billType}/{id}")
    @RequiresRole({ "user", "admin" })
    public ResponseEntity<BillResponseDTO> replaceExistingBill(
            @PathVariable String billType,
            @PathVariable Long id,
            @Valid @RequestBody BillRequestDTO billRequestDTO,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        Long userId = RequestUtil.getUserIdFromRequest(request);
        String role = RequestUtil.getRoleFromRequest(request);
        BillResponseDTO response = billService.replaceBill(id, billType, billRequestDTO, location, userId, role);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{billType}/{id}/payments/{paymentId}")
    @RequiresRole({ "user", "admin" })
    public ResponseEntity<BillResponseDTO> updatePaymentOnExistingBill(
            @PathVariable String billType,
            @PathVariable Long id,
            @PathVariable Long paymentId,
            @Valid @RequestBody BillPaymentRequestDTO paymentRequest,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        Long userId = RequestUtil.getUserIdFromRequest(request);
        BillResponseDTO response = billService.updatePaymentOnBill(id, billType, paymentId, paymentRequest, location, userId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{billType}/{id}/payments/{paymentId}")
    @RequiresRole({ "user", "admin" })
    public ResponseEntity<BillResponseDTO> deletePaymentOnExistingBill(
            @PathVariable String billType,
            @PathVariable Long id,
            @PathVariable Long paymentId,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        Long userId = RequestUtil.getUserIdFromRequest(request);
        BillResponseDTO response = billService.deletePaymentOnBill(id, billType, paymentId, location, userId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Partial stock return", description = "Record physical returns against an existing bill line. "
            + "Inventory ledger RETURN/IN is tied to this bill id; totals are capped per line (sold − already returned).")
    @PostMapping("/{billType}/{id}/stock-returns")
    @RequiresRole({ "user", "admin" })
    public ResponseEntity<BillStockReturnResponseDTO> recordPartialStockReturn(
            @PathVariable String billType,
            @PathVariable Long id,
            @Valid @RequestBody BillStockReturnRequestDTO request,
            HttpServletRequest requestHttp) {
        String location = RequestUtil.getLocationFromRequest(requestHttp);
        Long userId = RequestUtil.getUserIdFromRequest(requestHttp);
        BillStockReturnResponseDTO dto = billService.recordPartialStockReturn(id, billType, request, location, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Operation(summary = "Patch line quantities", description = "Update billed quantities on existing lines; stock is adjusted (return on decrease, sale on increase). "
            + "Cannot reduce a line that already has partial stock returns (use further returns instead).")
    @PatchMapping("/{billType}/{id}/line-quantities")
    @RequiresRole({ "user", "admin" })
    public ResponseEntity<BillResponseDTO> patchBillLineQuantities(
            @PathVariable String billType,
            @PathVariable Long id,
            @Valid @RequestBody BillLineQuantitiesPatchRequestDTO request,
            HttpServletRequest requestHttp) {
        String location = RequestUtil.getLocationFromRequest(requestHttp);
        Long userId = RequestUtil.getUserIdFromRequest(requestHttp);
        BillResponseDTO dto = billService.patchBillLineQuantities(id, billType, request, location, userId);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{billType}/{id}")
    @RequiresRole({ "user", "admin" })
    public ResponseEntity<Void> deleteBillByTypeAndId(
            @PathVariable String billType,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) BillCancelRequestDTO cancelBody,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        Long userId = RequestUtil.getUserIdFromRequest(request);
        String reason = cancelBody != null ? cancelBody.getReason() : null;
        billService.deleteBill(id, billType, location, userId, reason);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @RequiresRole({ "user", "admin" })
    public ResponseEntity<Void> deleteBillById(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) BillCancelRequestDTO cancelBody,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        Long userId = RequestUtil.getUserIdFromRequest(request);
        String reason = cancelBody != null ? cancelBody.getReason() : null;
        billService.deleteBillById(id, location, userId, reason);
        return ResponseEntity.noContent().build();
    }

    /**
     * Helper method to generate PDF bytes based on bill type
     */
    private byte[] generatePdfBytes(BillResponseDTO bill) throws Exception {
        // Check if this is a simple bill (no GST, no seller details)
        boolean isSimpleBill = (bill.getSimpleBill() != null && bill.getSimpleBill())
                || (bill.getTaxPercentage() != null && bill.getTaxPercentage() == 0);

        if (isSimpleBill) {
            return pdfService.generateSimpleBillPdf(bill);
        } else {
            return pdfService.generateBillPdf(bill);
        }
    }
}
