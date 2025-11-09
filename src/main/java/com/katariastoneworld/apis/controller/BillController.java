package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.dto.BillRequestDTO;
import com.katariastoneworld.apis.dto.BillResponseDTO;
import com.katariastoneworld.apis.service.BillService;
import com.katariastoneworld.apis.service.EmailService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/bills", "/bills"})
public class BillController {
    
    @Autowired
    private BillService billService;
    
    @Autowired
    private EmailService emailService;
    
    @PostMapping
    public ResponseEntity<BillResponseDTO> createBill(@Valid @RequestBody BillRequestDTO billRequestDTO) {
      System.out.println("Creating bill: " + billRequestDTO);
        BillResponseDTO response = billService.createBill(billRequestDTO);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    @GetMapping("/number/{billNumber}")
    public ResponseEntity<BillResponseDTO> getBillByBillNumber(@PathVariable String billNumber) {
        BillResponseDTO response = billService.getBillByBillNumber(billNumber);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/{billType}/{id}")
    public ResponseEntity<BillResponseDTO> getBillById(@PathVariable String billType, @PathVariable Long id) {
        BillResponseDTO response = billService.getBillById(id, billType);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping
    public ResponseEntity<List<BillResponseDTO>> getAllBills() {
        List<BillResponseDTO> bills = billService.getAllBills();
        return ResponseEntity.ok(bills);
    }
    
    @GetMapping("/sales")
    public ResponseEntity<List<BillResponseDTO>> getAllSales() {
        List<BillResponseDTO> sales = billService.getAllSales();
        return ResponseEntity.ok(sales);
    }
    
    @GetMapping("/customer/{mobileNumber}")
    public ResponseEntity<List<BillResponseDTO>> getBillsByMobileNumber(@PathVariable String mobileNumber) {
        List<BillResponseDTO> bills = billService.getBillsByMobileNumber(mobileNumber);
        return ResponseEntity.ok(bills);
    }
    
    @PostMapping("/test-email")
    public ResponseEntity<String> sendTestEmail(@RequestParam(required = false, defaultValue = "manjitsharma963@gmail.com") String email) {
        try {
            emailService.sendTestEmail(email);
            return ResponseEntity.ok("Test email sent successfully to: " + email);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to send test email: " + e.getMessage());
        }
    }
}
