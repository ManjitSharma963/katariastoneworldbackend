package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.ExpenseRequestDTO;
import com.katariastoneworld.apis.dto.ExpenseResponseDTO;
import com.katariastoneworld.apis.entity.Expense;
import com.katariastoneworld.apis.repository.ExpenseRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class ExpenseService {
    
    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private DailyBudgetService dailyBudgetService;

    public ExpenseResponseDTO createExpense(ExpenseRequestDTO requestDTO, String location) {
        Expense expense = new Expense();
        expense.setType(requestDTO.getType());
        expense.setCategory(requestDTO.getCategory());
        // Use request date if provided; otherwise today (avoids client sending yesterday due to timezone)
        expense.setDate(requestDTO.getDate() != null ? requestDTO.getDate() : LocalDate.now());
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
        if (LocalDate.now().equals(savedExpense.getDate()) && savedExpense.getAmount() != null) {
            dailyBudgetService.adjustRemainingForDailyExpense(location, savedExpense.getAmount().negate());
        }
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
        if (requestDTO.getDate() != null) {
            expense.setDate(requestDTO.getDate());
        }
        expense.setDescription(requestDTO.getDescription());
        expense.setAmount(requestDTO.getAmount());
        expense.setPaymentMethod(requestDTO.getPaymentMethod());
        expense.setEmployeeId(requestDTO.getEmployeeId());
        expense.setEmployeeName(requestDTO.getEmployeeName());
        expense.setMonth(requestDTO.getMonth());
        expense.setSettled(requestDTO.getSettled());
        
        BigDecimal oldAmount = expense.getAmount();
        LocalDate oldDate = expense.getDate();
        Expense updatedExpense = expenseRepository.save(expense);
        LocalDate today = LocalDate.now();
        if (today.equals(oldDate)) {
            dailyBudgetService.adjustRemainingForDailyExpense(location, oldAmount != null ? oldAmount : BigDecimal.ZERO);
        }
        if (today.equals(updatedExpense.getDate()) && updatedExpense.getAmount() != null) {
            dailyBudgetService.adjustRemainingForDailyExpense(location, updatedExpense.getAmount().negate());
        }
        return convertToResponseDTO(updatedExpense);
    }
    
    public void deleteExpense(Long id, String location) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Expense not found with id: " + id));
        
        // Verify location matches
        if (!location.equals(expense.getLocation())) {
            throw new RuntimeException("Expense not found with id: " + id);
        }
        if (LocalDate.now().equals(expense.getDate()) && expense.getAmount() != null) {
            dailyBudgetService.adjustRemainingForDailyExpense(location, expense.getAmount());
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

