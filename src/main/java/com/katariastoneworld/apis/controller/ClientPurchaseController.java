package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.ClientPurchasePaymentRequestDTO;
import com.katariastoneworld.apis.dto.ClientPurchasePaymentResponseDTO;
import com.katariastoneworld.apis.dto.ClientPurchaseRequestDTO;
import com.katariastoneworld.apis.dto.ClientPurchaseResponseDTO;
import com.katariastoneworld.apis.service.ClientPurchaseService;
import com.katariastoneworld.apis.util.RequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/client-purchases")
@Tag(name = "Client Purchases", description = "Client purchase management endpoints")
public class ClientPurchaseController {
    
    @Autowired
    private ClientPurchaseService clientPurchaseService;
    
    @Operation(
            summary = "Create a new client purchase",
            description = "Create a new client purchase record. Location is automatically extracted from JWT token."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Client purchase created successfully",
                    content = @Content(schema = @Schema(implementation = ClientPurchaseResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @RequiresRole({"user", "admin"})
    public ResponseEntity<ClientPurchaseResponseDTO> createClientPurchase(
            @Valid @RequestBody ClientPurchaseRequestDTO requestDTO,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        ClientPurchaseResponseDTO response = clientPurchaseService.createClientPurchase(requestDTO, location);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    @Operation(
            summary = "Get all client purchases",
            description = "Get all client purchases for the authenticated user's location. Results are ordered by purchase date (newest first)."
    )
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @GetMapping
    @RequiresRole("admin")
    public ResponseEntity<List<ClientPurchaseResponseDTO>> getAllClientPurchases(HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        List<ClientPurchaseResponseDTO> purchases = clientPurchaseService.getAllClientPurchases(location);
        return ResponseEntity.ok(purchases);
    }
    
    @Operation(
            summary = "Get client purchase by ID",
            description = "Get a specific client purchase by its ID"
    )
    @Parameter(name = "id", description = "Client purchase ID", required = true)
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{id}")
    @RequiresRole("admin")
    public ResponseEntity<ClientPurchaseResponseDTO> getClientPurchaseById(
            @PathVariable Long id,
            HttpServletRequest request) {
        try {
            String location = RequestUtil.getLocationFromRequest(request);
            ClientPurchaseResponseDTO purchase = clientPurchaseService.getClientPurchaseById(id, location);
            return ResponseEntity.ok(purchase);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
    
    @Operation(
            summary = "Update client purchase",
            description = "Update an existing client purchase"
    )
    @Parameter(name = "id", description = "Client purchase ID", required = true)
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{id}")
    @RequiresRole("admin")
    public ResponseEntity<ClientPurchaseResponseDTO> updateClientPurchase(
            @PathVariable Long id,
            @Valid @RequestBody ClientPurchaseRequestDTO requestDTO,
            HttpServletRequest request) {
        try {
            String location = RequestUtil.getLocationFromRequest(request);
            ClientPurchaseResponseDTO response = clientPurchaseService.updateClientPurchase(id, requestDTO, location);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
    
    @Operation(
            summary = "Delete client purchase",
            description = "Delete a client purchase by ID"
    )
    @Parameter(name = "id", description = "Client purchase ID", required = true)
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}")
    @RequiresRole("admin")
    public ResponseEntity<Void> deleteClientPurchase(
            @PathVariable Long id,
            HttpServletRequest request) {
        try {
            String location = RequestUtil.getLocationFromRequest(request);
            clientPurchaseService.deleteClientPurchase(id, location);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
    
    @Operation(
            summary = "Create a payment for a client purchase",
            description = "Create a payment transaction for a specific client purchase"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Payment created successfully",
                    content = @Content(schema = @Schema(implementation = ClientPurchasePaymentResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Client purchase not found")
    })
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/payments")
    @RequiresRole({"user", "admin"})
    public ResponseEntity<ClientPurchasePaymentResponseDTO> createPayment(
            @PathVariable Long id,
            @Valid @RequestBody ClientPurchasePaymentRequestDTO requestDTO,
            HttpServletRequest request) {
        try {
            String location = RequestUtil.getLocationFromRequest(request);
            ClientPurchasePaymentResponseDTO response = clientPurchaseService.createPayment(id, requestDTO, location);
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
    
    @Operation(
            summary = "Get all payments",
            description = "Get all payment transactions for the authenticated user's location. Results are ordered by payment date (newest first)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "All payments retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ClientPurchasePaymentResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @GetMapping("/payments")
    @RequiresRole("admin")
    public ResponseEntity<List<ClientPurchasePaymentResponseDTO>> getAllPayments(HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        List<ClientPurchasePaymentResponseDTO> payments = clientPurchaseService.getAllPayments(location);
        return ResponseEntity.ok(payments);
    }
    
    @Operation(
            summary = "Get payment history for a client purchase",
            description = "Get all payment transactions for a specific client purchase. Results are ordered by payment date (newest first)."
    )
    @Parameter(name = "id", description = "Client purchase ID", required = true)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment history retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ClientPurchasePaymentResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Client purchase not found")
    })
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{id}/payments")
    @RequiresRole("admin")
    public ResponseEntity<List<ClientPurchasePaymentResponseDTO>> getPaymentHistory(
            @PathVariable Long id,
            HttpServletRequest request) {
        try {
            String location = RequestUtil.getLocationFromRequest(request);
            List<ClientPurchasePaymentResponseDTO> payments = clientPurchaseService.getPaymentHistory(id, location);
            return ResponseEntity.ok(payments);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}

