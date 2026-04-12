package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.ExpenseRequestDTO;
import com.katariastoneworld.apis.dto.ExpenseResponseDTO;
import com.katariastoneworld.apis.entity.Expense;
import com.katariastoneworld.apis.entity.ExpenseCategory;
import com.katariastoneworld.apis.entity.ReferenceType;
import com.katariastoneworld.apis.repository.ExpenseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class ExpenseService {
    private static final Logger log = LoggerFactory.getLogger(ExpenseService.class);
    
    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private DailyBudgetService dailyBudgetService;

    @Autowired
    private LoanLedgerService loanLedgerService;

    public ExpenseResponseDTO createExpense(ExpenseRequestDTO requestDTO, String location) {
        Expense expense = new Expense();
        String normalizedType = normalizeTypeForEmployeeCategory(requestDTO.getType(), requestDTO.getCategory());
        expense.setType(normalizedType);
        expense.setCategory(requestDTO.getCategory());
        expense.setDate(requestDTO.getDate() != null ? requestDTO.getDate() : LocalDate.now());
        expense.setDescription(requestDTO.getDescription());
        expense.setAmount(requestDTO.getAmount());
        expense.setPaymentMethod(requestDTO.getPaymentMethod());
        expense.setLocation(location);
        expense.setExpenseCategory(resolveExpenseCategory(requestDTO, normalizedType));
        expense.setReferenceType(resolveReferenceType(requestDTO));
        expense.setReferenceId(trimToNull(requestDTO.getReferenceId()));
        
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
        } else if ("advance".equalsIgnoreCase(normalizedType)) {
            expense.setSettled(false); // Default to false for advance expenses
        }
        
        Expense savedExpense = expenseRepository.save(expense);
        log.info("expense_create location={} id={} amount={} category={} date={}",
                location, savedExpense.getId(), savedExpense.getAmount(), savedExpense.getCategory(), savedExpense.getDate());
        if (LocalDate.now().equals(savedExpense.getDate())
                && savedExpense.getAmount() != null
                && shouldAffectDailyInHandBudget(savedExpense)) {
            dailyBudgetService.adjustRemainingForDailyExpense(location, savedExpense.getAmount().negate());
        }
        loanLedgerService.syncRepaymentLedger(savedExpense, requestDTO.getLenderId());
        return convertToResponseDTO(savedExpense);
    }
    
    public List<ExpenseResponseDTO> getAllExpenses(String location) {
        if (location == null || location.trim().isEmpty()) {
            throw new RuntimeException("Location is required to fetch expenses.");
        }
        return expenseRepository.findByLocationOrderByDateDesc(location).stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }
    
    public ExpenseResponseDTO getExpenseById(Long id, String location) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Expense not found with id: " + id));
        if (!location.equals(expense.getLocation())) {
            throw new RuntimeException("Expense not found with id: " + id);
        }
        return convertToResponseDTO(expense);
    }
    
    public ExpenseResponseDTO updateExpense(Long id, ExpenseRequestDTO requestDTO, String location) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Expense not found with id: " + id));
        if (!location.equals(expense.getLocation())) {
            throw new RuntimeException("Expense not found with id: " + id);
        }

        // Keep previous values for budget rollback/re-apply logic
        BigDecimal oldAmount = expense.getAmount();
        LocalDate oldDate = expense.getDate();
        String oldPaymentMethod = expense.getPaymentMethod();
        ExpenseCategory oldExpenseCategory = expense.getExpenseCategory();
        String oldCategoryRaw = expense.getCategory();
        String oldType = expense.getType();
        
        String normalizedType = normalizeTypeForEmployeeCategory(requestDTO.getType(), requestDTO.getCategory());
        expense.setType(normalizedType);
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
        expense.setExpenseCategory(resolveExpenseCategory(requestDTO, normalizedType));
        expense.setReferenceType(resolveReferenceType(requestDTO));
        expense.setReferenceId(trimToNull(requestDTO.getReferenceId()));
        if (requestDTO.getSettled() != null) {
            expense.setSettled(requestDTO.getSettled());
        } else if ("advance".equalsIgnoreCase(normalizedType) && expense.getSettled() == null) {
            expense.setSettled(false);
        }

        Expense oldExpenseSnapshot = new Expense();
        oldExpenseSnapshot.setAmount(oldAmount);
        oldExpenseSnapshot.setDate(oldDate);
        oldExpenseSnapshot.setPaymentMethod(oldPaymentMethod);
        oldExpenseSnapshot.setExpenseCategory(oldExpenseCategory);
        oldExpenseSnapshot.setCategory(oldCategoryRaw);
        oldExpenseSnapshot.setType(oldType);

        Expense updatedExpense = expenseRepository.save(expense);
        log.info("expense_update location={} id={} amount={} category={} date={}",
                location, updatedExpense.getId(), updatedExpense.getAmount(), updatedExpense.getCategory(), updatedExpense.getDate());
        LocalDate today = LocalDate.now();
        if (today.equals(oldDate) && shouldAffectDailyInHandBudget(oldExpenseSnapshot)) {
            dailyBudgetService.adjustRemainingForDailyExpense(location, oldAmount != null ? oldAmount : BigDecimal.ZERO);
        }
        if (today.equals(updatedExpense.getDate())
                && updatedExpense.getAmount() != null
                && shouldAffectDailyInHandBudget(updatedExpense)) {
            dailyBudgetService.adjustRemainingForDailyExpense(location, updatedExpense.getAmount().negate());
        }
        loanLedgerService.syncRepaymentLedger(updatedExpense, requestDTO.getLenderId());
        return convertToResponseDTO(updatedExpense);
    }
    
    public void deleteExpense(Long id, String location) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Expense not found with id: " + id));
        if (!location.equals(expense.getLocation())) {
            throw new RuntimeException("Expense not found with id: " + id);
        }
        loanLedgerService.deleteRepaymentByExpenseId(expense.getId());
        if (LocalDate.now().equals(expense.getDate())
                && expense.getAmount() != null
                && shouldAffectDailyInHandBudget(expense)) {
            dailyBudgetService.adjustRemainingForDailyExpense(expense.getLocation(), expense.getAmount());
        }
        log.info("expense_delete location={} id={} amount={} date={}",
                expense.getLocation(), expense.getId(), expense.getAmount(), expense.getDate());
        expense.setIsDeleted(true);
        expenseRepository.save(expense);
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
        dto.setExpenseCategory(expense.getExpenseCategory() != null ? expense.getExpenseCategory().name() : null);
        dto.setReferenceType(expense.getReferenceType() != null ? expense.getReferenceType().name() : null);
        dto.setReferenceId(expense.getReferenceId());
        loanLedgerService.findLenderIdForExpense(expense.getId()).ifPresent(dto::setLenderId);
        dto.setCreatedAt(expense.getCreatedAt());
        dto.setUpdatedAt(expense.getUpdatedAt());
        return dto;
    }

    /**
     * Business rule: when expense category is employee, treat it as employee advance so salary settlement
     * can deduct it in payroll flows.
     */
    private static String normalizeTypeForEmployeeCategory(String requestType, String category) {
        if (category != null && "employee".equalsIgnoreCase(category.trim())) {
            return "advance";
        }
        return requestType;
    }

    private static ExpenseCategory resolveExpenseCategory(ExpenseRequestDTO dto, String normalizedType) {
        if (dto != null && dto.getExpenseCategory() != null && !dto.getExpenseCategory().isBlank()) {
            return ExpenseCategory.parseFlexible(dto.getExpenseCategory());
        }
        if (dto != null && dto.getCategory() != null) {
            String c = dto.getCategory().trim().toLowerCase();
            if ("loan_repayment".equals(c) || "loan_repay".equals(c) || "loan".equals(c) || "market_loan".equals(c)) {
                return ExpenseCategory.LOAN;
            }
        }
        String t = normalizedType == null ? "" : normalizedType.trim().toLowerCase();
        if ("salary".equals(t) || "advance".equals(t)) return ExpenseCategory.SALARY;
        if (dto != null && dto.getCategory() != null) {
            String c = dto.getCategory().trim().toLowerCase();
            if ("inventory".equals(c) || "stock".equals(c) || "purchase".equals(c) || "client".equals(c)) {
                return ExpenseCategory.INVENTORY;
            }
        }
        return ExpenseCategory.DAILY;
    }

    private static ReferenceType resolveReferenceType(ExpenseRequestDTO dto) {
        if (dto != null && dto.getReferenceType() != null && !dto.getReferenceType().isBlank()) {
            return ReferenceType.parseFlexible(dto.getReferenceType());
        }
        return ReferenceType.DIRECT;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Rule: loan repayments paid by non in-hand modes (bank transfer / cheque) should not affect
     * daily in-hand budget. All other expenses keep existing behavior.
     */
    private static boolean shouldAffectDailyInHandBudget(Expense e) {
        if (e == null) return false;
        if (!isLoanRepayment(e)) return true;
        String pm = e.getPaymentMethod() == null ? "" : e.getPaymentMethod().trim().toLowerCase();
        return "cash".equals(pm) || "upi".equals(pm);
    }

    private static boolean isLoanRepayment(Expense e) {
        if (e == null) return false;
        if (e.getExpenseCategory() == ExpenseCategory.LOAN) return true;
        String c = e.getCategory() == null ? "" : e.getCategory().trim().toLowerCase();
        return "loan_repayment".equals(c) || "loan_repay".equals(c) || "loan".equals(c) || "market_loan".equals(c);
    }
}

