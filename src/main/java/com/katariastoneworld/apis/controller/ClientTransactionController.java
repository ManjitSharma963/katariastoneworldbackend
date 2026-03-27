package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.ClientTransactionRequestDTO;
import com.katariastoneworld.apis.dto.ClientTransactionResponseDTO;
import com.katariastoneworld.apis.service.ClientTransactionService;
import com.katariastoneworld.apis.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping(value = {"/api/client-transactions", "client-transactions"}, produces = "application/json")
public class ClientTransactionController {

    @Autowired
    private ClientTransactionService clientTransactionService;

    @PostMapping(consumes = "application/json")
    @RequiresRole({"user", "admin"})
    public ResponseEntity<ClientTransactionResponseDTO> create(
            @Valid @RequestBody ClientTransactionRequestDTO requestDTO,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        Long actorUserId = RequestUtil.getUserIdFromRequest(request);
        ClientTransactionResponseDTO created = clientTransactionService.create(requestDTO, location, actorUserId);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @GetMapping
    @RequiresRole("admin")
    public ResponseEntity<List<ClientTransactionResponseDTO>> list(
            @RequestParam(value = "from", required = false) LocalDate from,
            @RequestParam(value = "to", required = false) LocalDate to,
            @RequestParam(value = "transactionType", required = false) String transactionType,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(clientTransactionService.list(location, from, to, transactionType));
    }
}

