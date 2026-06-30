package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.AgentCommissionHistoryDTO;
import com.katariastoneworld.apis.dto.AgentCommissionStatusUpdateDTO;
import com.katariastoneworld.apis.dto.AgentCommissionStatusUpdateResponseDTO;
import com.katariastoneworld.apis.dto.AssignBillAgentCommissionDTO;
import com.katariastoneworld.apis.dto.BillAgentAssignmentResponseDTO;
import com.katariastoneworld.apis.dto.SalesAgentRequestDTO;
import com.katariastoneworld.apis.dto.SalesAgentResponseDTO;
import com.katariastoneworld.apis.service.SalesAgentService;
import com.katariastoneworld.apis.util.RequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({ "/api/sales-agents", "/sales-agents" })
@Tag(name = "Sales Agents", description = "Sales agent master data and commission history")
public class SalesAgentController {

    private final SalesAgentService salesAgentService;

    public SalesAgentController(SalesAgentService salesAgentService) {
        this.salesAgentService = salesAgentService;
    }

    @Operation(summary = "List sales agents for your location")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    @RequiresRole({ "user", "admin" })
    public ResponseEntity<List<SalesAgentResponseDTO>> list(
            HttpServletRequest request,
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(salesAgentService.list(location, activeOnly));
    }

    @Operation(summary = "Get agent by id")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{id}")
    @RequiresRole({ "user", "admin" })
    public ResponseEntity<SalesAgentResponseDTO> get(@PathVariable Long id, HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(salesAgentService.getById(id, location));
    }

    @Operation(summary = "Create sales agent")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @RequiresRole("admin")
    public ResponseEntity<SalesAgentResponseDTO> create(
            @Valid @RequestBody SalesAgentRequestDTO body,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return new ResponseEntity<>(salesAgentService.create(body, location), HttpStatus.CREATED);
    }

    @Operation(summary = "Update sales agent")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{id}")
    @RequiresRole("admin")
    public ResponseEntity<SalesAgentResponseDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody SalesAgentRequestDTO body,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(salesAgentService.update(id, body, location));
    }

    @Operation(summary = "Delete sales agent (only when no bills are linked)")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}")
    @RequiresRole("admin")
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        salesAgentService.delete(id, location);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Commission / deal history for an agent")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{id}/commissions")
    @RequiresRole({ "user", "admin" })
    public ResponseEntity<List<AgentCommissionHistoryDTO>> commissionHistory(
            @PathVariable Long id,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(salesAgentService.commissionHistory(id, location));
    }

    @Operation(summary = "Mark agent commission as paid or pending")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/commissions/status")
    @RequiresRole({ "user", "admin" })
    public ResponseEntity<AgentCommissionStatusUpdateResponseDTO> updateCommissionStatus(
            @Valid @RequestBody AgentCommissionStatusUpdateDTO body,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(salesAgentService.updateCommissionStatus(
                body.getBillType(), body.getBillId(), body.getStatus(), location));
    }

    @Operation(summary = "Assign or update sales agent on an existing bill")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/commissions/assign")
    @RequiresRole({ "user", "admin" })
    public ResponseEntity<BillAgentAssignmentResponseDTO> assignAgentToBill(
            @Valid @RequestBody AssignBillAgentCommissionDTO body,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(salesAgentService.assignAgentToBill(body, location));
    }
}
