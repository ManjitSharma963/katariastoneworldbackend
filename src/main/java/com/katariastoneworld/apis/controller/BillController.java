package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.BillRequestDTO;
import com.katariastoneworld.apis.dto.BillResponseDTO;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/bills", "/bills"})
@Tag(name = "Bills", description = "Bill management endpoints for GST and Non-GST bills")
public class BillController {
    
    @Autowired
    private BillService billService;
    
    @Autowired
    private EmailService emailService;
    
    @Autowired
    private PdfService pdfService;
    
    @Operation(
            summary = "Create a new bill",
            description = "Create a new bill (GST or Non-GST) with customer and items. Location is automatically extracted from JWT token."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Bill created successfully",
                    content = @Content(schema = @Schema(implementation = BillResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @RequiresRole({"user", "admin"})
    public ResponseEntity<BillResponseDTO> createBill(@Valid @RequestBody BillRequestDTO billRequestDTO, HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        System.out.println("Creating bill: " + billRequestDTO);
        BillResponseDTO response = billService.createBill(billRequestDTO, location);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    @Operation(
            summary = "Get bill by bill number",
            description = "Retrieve a bill by its unique bill number"
    )
    @Parameter(name = "billNumber", description = "Bill number (sequential number: 1, 2, 3, etc.)", required = true)
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @GetMapping("/number/{billNumber}")
    @RequiresRole("admin")
    public ResponseEntity<BillResponseDTO> getBillByBillNumber(@PathVariable String billNumber, HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        BillResponseDTO response = billService.getBillByBillNumber(billNumber, location);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/{billType}/{id}")
    @RequiresRole("admin")
    public ResponseEntity<BillResponseDTO> getBillById(@PathVariable String billType, @PathVariable Long id, HttpServletRequest request) {
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
    
    @Operation(
            summary = "Get all bills",
            description = "Get all bills for the authenticated user's location. Requires admin role."
    )
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @GetMapping
    @RequiresRole("admin")
    public ResponseEntity<List<BillResponseDTO>> getAllBills(HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        List<BillResponseDTO> bills = billService.getAllBills(location);
        return ResponseEntity.ok(bills);
    }
    
    @GetMapping("/sales")
    @RequiresRole("admin")
    public ResponseEntity<List<BillResponseDTO>> getAllSales(HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        List<BillResponseDTO> sales = billService.getAllSales(location);
        return ResponseEntity.ok(sales);
    }
    
    @GetMapping("/customer/{mobileNumber}")
    @RequiresRole("admin")
    public ResponseEntity<List<BillResponseDTO>> getBillsByMobileNumber(@PathVariable String mobileNumber) {
        List<BillResponseDTO> bills = billService.getBillsByMobileNumber(mobileNumber);
        return ResponseEntity.ok(bills);
    }
    
    @PostMapping("/test-email")
    @RequiresRole("admin")
    public ResponseEntity<String> sendTestEmail(@RequestParam(required = false, defaultValue = "manjitsharma963@gmail.com") String email) {
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
    @Operation(
            summary = "Download bill PDF by bill number",
            description = "Download the PDF version of a bill by its bill number"
    )
    @Parameter(name = "billNumber", description = "Bill number", required = true)
    @ApiResponse(responseCode = "200", description = "PDF file downloaded successfully",
            content = @Content(mediaType = "application/pdf"))
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @GetMapping("/number/{billNumber}/download")
    @RequiresRole("admin")
    public ResponseEntity<byte[]> downloadBillByNumber(@PathVariable String billNumber, HttpServletRequest request) {
        try {
            String location = RequestUtil.getLocationFromRequest(request);
            BillResponseDTO bill = billService.getBillByBillNumber(billNumber, location);
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
            System.err.println("Error downloading GST bill with id " + id + ": " + e.getMessage());
            e.printStackTrace();
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
    public ResponseEntity<byte[]> downloadBillById(@PathVariable String billType, @PathVariable Long id, HttpServletRequest request) {
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
