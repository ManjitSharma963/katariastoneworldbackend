package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.CustomerAdvanceCreateRequestDTO;
import com.katariastoneworld.apis.dto.CustomerAdvanceHistoryEntryDTO;
import com.katariastoneworld.apis.dto.CustomerAdvanceResponseDTO;
import com.katariastoneworld.apis.dto.CustomerAdvanceSummaryDTO;
import com.katariastoneworld.apis.service.CustomerAdvanceService;
import com.katariastoneworld.apis.util.RequestUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customer/advance")
@Tag(name = "Customer advance", description = "Token / advance payments (does not alter bills or bill_payments schema)")
public class CustomerAdvanceController {

    @Autowired
    private CustomerAdvanceService customerAdvanceService;

    @PostMapping
    @RequiresRole({"user", "admin"})
    public ResponseEntity<CustomerAdvanceResponseDTO> createAdvance(
            @Valid @RequestBody CustomerAdvanceCreateRequestDTO body,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        CustomerAdvanceResponseDTO dto = customerAdvanceService.createAdvance(body, location);
        return new ResponseEntity<>(dto, HttpStatus.CREATED);
    }

    @GetMapping("/summary")
    @RequiresRole({"user", "admin"})
    public ResponseEntity<CustomerAdvanceSummaryDTO> getSummary(
            @RequestParam("customerId") Long customerId,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(customerAdvanceService.getSummary(customerId, location));
    }

    @GetMapping("/history")
    @RequiresRole({"user", "admin"})
    public ResponseEntity<List<CustomerAdvanceHistoryEntryDTO>> getHistory(
            @RequestParam("customerId") Long customerId,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(customerAdvanceService.getHistory(customerId, location));
    }
}
