package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.SupplierRequestDTO;
import com.katariastoneworld.apis.dto.SupplierResponseDTO;
import com.katariastoneworld.apis.service.SupplierService;
import com.katariastoneworld.apis.util.RequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({ "/api/suppliers", "/suppliers" })
@Tag(name = "Suppliers", description = "Supplier (firm) master data — scoped by JWT location")
public class SupplierController {

    @Autowired
    private SupplierService supplierService;

    @Operation(summary = "List suppliers for your location")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    @RequiresRole({ "user", "admin" })
    public ResponseEntity<List<SupplierResponseDTO>> list(HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(supplierService.list(location));
    }

    @Operation(summary = "Create supplier")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @RequiresRole("admin")
    public ResponseEntity<SupplierResponseDTO> create(
            @Valid @RequestBody SupplierRequestDTO body,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        SupplierResponseDTO dto = supplierService.create(body, location);
        return new ResponseEntity<>(dto, HttpStatus.CREATED);
    }
}
