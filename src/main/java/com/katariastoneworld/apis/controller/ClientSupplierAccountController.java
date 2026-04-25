package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.ClientSupplierAccountRequestDTO;
import com.katariastoneworld.apis.dto.ClientSupplierAccountResponseDTO;
import com.katariastoneworld.apis.dto.ClientSupplierAccountUpdateDTO;
import com.katariastoneworld.apis.service.ClientSupplierAccountService;
import com.katariastoneworld.apis.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/client-supplier-accounts")
public class ClientSupplierAccountController {

    @Autowired
    private ClientSupplierAccountService clientSupplierAccountService;

    @GetMapping
    @RequiresRole("admin")
    public ResponseEntity<List<ClientSupplierAccountResponseDTO>> list(HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(clientSupplierAccountService.list(location));
    }

    @PostMapping(consumes = "application/json")
    @RequiresRole("admin")
    public ResponseEntity<ClientSupplierAccountResponseDTO> create(
            @Valid @RequestBody ClientSupplierAccountRequestDTO body,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        try {
            return new ResponseEntity<>(clientSupplierAccountService.create(location, body), HttpStatus.CREATED);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PutMapping(value = "/{id}", consumes = "application/json")
    @RequiresRole("admin")
    public ResponseEntity<ClientSupplierAccountResponseDTO> update(
            @PathVariable Long id,
            @RequestBody ClientSupplierAccountUpdateDTO body,
            HttpServletRequest request) {
        try {
            String location = RequestUtil.getLocationFromRequest(request);
            return ResponseEntity.ok(clientSupplierAccountService.update(id, location, body));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
