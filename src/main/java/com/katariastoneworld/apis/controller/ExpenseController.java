package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.ExpenseRequestDTO;
import com.katariastoneworld.apis.dto.ExpenseResponseDTO;
import com.katariastoneworld.apis.service.ExpenseService;
import com.katariastoneworld.apis.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = {"/api/expenses","expenses"}, produces = "application/json")
@RequiresRole("admin")
public class ExpenseController {
    
    @Autowired
    private ExpenseService expenseService;
    
    @PostMapping(consumes = "application/json")
    public ResponseEntity<ExpenseResponseDTO> createExpense(@Valid @RequestBody ExpenseRequestDTO requestDTO, HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        ExpenseResponseDTO response = expenseService.createExpense(requestDTO, location);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    @GetMapping
    public ResponseEntity<List<ExpenseResponseDTO>> getAllExpenses(HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        List<ExpenseResponseDTO> expenses = expenseService.getAllExpenses(location);
        return new ResponseEntity<>(expenses, HttpStatus.OK);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ExpenseResponseDTO> getExpenseById(@PathVariable Long id, HttpServletRequest request) {
        try {
            String location = RequestUtil.getLocationFromRequest(request);
            ExpenseResponseDTO expense = expenseService.getExpenseById(id, location);
            return ResponseEntity.ok(expense);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<ExpenseResponseDTO> updateExpense(@PathVariable Long id, @Valid @RequestBody ExpenseRequestDTO requestDTO, HttpServletRequest request) {
        try {
            String location = RequestUtil.getLocationFromRequest(request);
            ExpenseResponseDTO response = expenseService.updateExpense(id, requestDTO, location);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExpense(@PathVariable Long id, HttpServletRequest request) {
        try {
            String location = RequestUtil.getLocationFromRequest(request);
            expenseService.deleteExpense(id, location);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}

