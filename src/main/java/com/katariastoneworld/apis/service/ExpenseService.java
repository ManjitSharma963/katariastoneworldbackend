package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.ExpenseRequestDTO;
import com.katariastoneworld.apis.dto.ExpenseResponseDTO;
import com.katariastoneworld.apis.entity.Expense;
import com.katariastoneworld.apis.repository.ExpenseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class ExpenseService {
    
    @Autowired
    private ExpenseRepository expenseRepository;
    
    public ExpenseResponseDTO createExpense(ExpenseRequestDTO requestDTO, String location) {
        Expense expense = new Expense();
        expense.setType(requestDTO.getType());
        expense.setCategory(requestDTO.getCategory());
        expense.setDate(requestDTO.getDate());
        expense.setDescription(requestDTO.getDescription());
        expense.setAmount(requestDTO.getAmount());
        expense.setPaymentMethod(requestDTO.getPaymentMethod());
        expense.setLocation(location);
        
        // Set employee-related fields if provided
        if (requestDTO.getEmployeeId() != null) {
            expense.setEmployeeId(requestDTO.getEmployeeId());
        }
        if (requestDTO.getEmployeeName() != null) {
            expense.setEmployeeName(requestDTO.getEmployeeName());
        }
        
        // Set month for salary expenses
        if (requestDTO.getMonth() != null) {
            expense.setMonth(requestDTO.getMonth());
        }
        
        // Set settled for advance expenses
        if (requestDTO.getSettled() != null) {
            expense.setSettled(requestDTO.getSettled());
        } else if ("advance".equalsIgnoreCase(requestDTO.getType())) {
            expense.setSettled(false); // Default to false for advance expenses
        }
        
        Expense savedExpense = expenseRepository.save(expense);
        return convertToResponseDTO(savedExpense);
    }
    
    public List<ExpenseResponseDTO> getAllExpenses(String location) {
        return expenseRepository.findByLocation(location).stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }
    
    public ExpenseResponseDTO getExpenseById(Long id, String location) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Expense not found with id: " + id));
        
        // Verify location matches
        if (!location.equals(expense.getLocation())) {
            throw new RuntimeException("Expense not found with id: " + id);
        }
        
        return convertToResponseDTO(expense);
    }
    
    public ExpenseResponseDTO updateExpense(Long id, ExpenseRequestDTO requestDTO, String location) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Expense not found with id: " + id));
        
        // Verify location matches
        if (!location.equals(expense.getLocation())) {
            throw new RuntimeException("Expense not found with id: " + id);
        }
        
        expense.setType(requestDTO.getType());
        expense.setCategory(requestDTO.getCategory());
        expense.setDate(requestDTO.getDate());
        expense.setDescription(requestDTO.getDescription());
        expense.setAmount(requestDTO.getAmount());
        expense.setPaymentMethod(requestDTO.getPaymentMethod());
        expense.setEmployeeId(requestDTO.getEmployeeId());
        expense.setEmployeeName(requestDTO.getEmployeeName());
        expense.setMonth(requestDTO.getMonth());
        expense.setSettled(requestDTO.getSettled());
        
        Expense updatedExpense = expenseRepository.save(expense);
        return convertToResponseDTO(updatedExpense);
    }
    
    public void deleteExpense(Long id, String location) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Expense not found with id: " + id));
        
        // Verify location matches
        if (!location.equals(expense.getLocation())) {
            throw new RuntimeException("Expense not found with id: " + id);
        }
        
        expenseRepository.deleteById(id);
    }
    
    private ExpenseResponseDTO convertToResponseDTO(Expense expense) {
        ExpenseResponseDTO dto = new ExpenseResponseDTO();
        dto.setId(expense.getId());
        dto.setType(expense.getType());
        dto.setCategory(expense.getCategory());
        dto.setDate(expense.getDate());
        dto.setDescription(expense.getDescription());
        dto.setAmount(expense.getAmount());
        dto.setPaymentMethod(expense.getPaymentMethod());
        dto.setEmployeeId(expense.getEmployeeId());
        dto.setEmployeeName(expense.getEmployeeName());
        dto.setMonth(expense.getMonth());
        dto.setSettled(expense.getSettled());
        dto.setCreatedAt(expense.getCreatedAt());
        dto.setUpdatedAt(expense.getUpdatedAt());
        return dto;
    }
}

