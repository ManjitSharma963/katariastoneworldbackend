package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.DailyBudgetRequestDTO;
import com.katariastoneworld.apis.dto.DailyBudgetStatusDTO;
import com.katariastoneworld.apis.entity.DailyBudget;
import com.katariastoneworld.apis.entity.Expense;
import com.katariastoneworld.apis.repository.DailyBudgetRepository;
import com.katariastoneworld.apis.repository.ExpenseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.katariastoneworld.apis.dto.DailyBudgetSummaryDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class DailyBudgetService {

    @Autowired
    private DailyBudgetRepository dailyBudgetRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    /**
     * Get all budgets from the daily_budget table (all locations).
     */
    public List<DailyBudgetSummaryDTO> getAllBudgets() {
        return dailyBudgetRepository.findAll().stream()
                .map(b -> {
                    DailyBudgetSummaryDTO dto = new DailyBudgetSummaryDTO();
                    dto.setId(b.getId());
                    dto.setLocation(b.getLocation());
                    dto.setAmount(b.getAmount());
                    dto.setRemainingBudget(b.getRemainingBudget());
                    dto.setCreatedAt(b.getCreatedAt());
                    dto.setUpdatedAt(b.getUpdatedAt());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get daily budget status: budget amount, spent today (all expenses for the date), and remaining.
     * If no budget is set, budgetAmount and remainingAmount are 0; spentAmount is still returned.
     */
    public DailyBudgetStatusDTO getBudgetStatus(String location) {
        return getBudgetStatus(location, LocalDate.now());
    }

    /**
     * Get daily budget status for a specific date.
     */
    public DailyBudgetStatusDTO getBudgetStatus(String location, LocalDate date) {
        BigDecimal budgetAmount = BigDecimal.ZERO;
        BigDecimal storedRemaining = null;
        DailyBudget budget = dailyBudgetRepository.findByLocation(location).orElse(null);
        if (budget != null && budget.getAmount() != null) {
            budgetAmount = budget.getAmount();
            storedRemaining = budget.getRemainingBudget();
        }

        List<Expense> todayExpenses = expenseRepository.findByLocationAndDate(location, date);
        BigDecimal spentAmount = todayExpenses.stream()
                .map(Expense::getAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal computedRemaining = budgetAmount.subtract(spentAmount);
        // Use stored remaining_budget when available (for carry-over); otherwise use computed
        BigDecimal remainingAmount = (storedRemaining != null && date.equals(LocalDate.now()))
                ? storedRemaining : computedRemaining;
        // Backfill remaining_budget for existing rows (e.g. after adding the column)
        if (budget != null && date.equals(LocalDate.now()) && budget.getRemainingBudget() == null && budget.getAmount() != null) {
            budget.setRemainingBudget(computedRemaining);
            dailyBudgetRepository.save(budget);
            remainingAmount = computedRemaining;
        }

        DailyBudgetStatusDTO dto = new DailyBudgetStatusDTO();
        dto.setBudgetAmount(budgetAmount);
        dto.setSpentAmount(spentAmount);
        dto.setRemainingAmount(remainingAmount);
        dto.setDate(date);
        dto.setLocation(location);
        return dto;
    }

    /**
     * Set or update daily budget for the location. Creates a new record if none exists.
     * If a budget already exists, the amount is replaced with the new value and remaining_budget
     * is set to (new amount - today's spent) so it stays in sync.
     */
    public DailyBudgetStatusDTO setBudget(String location, DailyBudgetRequestDTO requestDTO) {
        BigDecimal newAmount = requestDTO.getAmount() != null ? requestDTO.getAmount() : BigDecimal.ZERO;
        DailyBudget budget = dailyBudgetRepository.findByLocation(location).orElse(null);
        if (budget == null) {
            budget = new DailyBudget();
            budget.setLocation(location);
            budget.setAmount(newAmount);
            budget.setRemainingBudget(newAmount);
        } else {
            budget.setAmount(newAmount);
            List<Expense> todayExpenses = expenseRepository.findByLocationAndDate(location, LocalDate.now());
            BigDecimal spentToday = todayExpenses.stream()
                    .map(Expense::getAmount)
                    .filter(a -> a != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            budget.setRemainingBudget(newAmount.subtract(spentToday));
        }
        dailyBudgetRepository.save(budget);
        return getBudgetStatus(location);
    }

    /**
     * Delete daily budget for the location. No-op if none exists.
     * @return true if a budget was deleted, false if none existed
     */
    public boolean deleteBudget(String location) {
        return dailyBudgetRepository.findByLocation(location)
                .map(budget -> {
                    dailyBudgetRepository.delete(budget);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Adjust stored remaining budget when any expense is added/updated/deleted for today.
     * @param location budget location
     * @param delta amount to add to remaining (negative when expense added, positive when expense removed/reduced)
     */
    public void adjustRemainingForDailyExpense(String location, BigDecimal delta) {
        if (delta == null || delta.compareTo(BigDecimal.ZERO) == 0) return;
        dailyBudgetRepository.findByLocation(location).ifPresent(budget -> {
            BigDecimal current = budget.getRemainingBudget() != null ? budget.getRemainingBudget() : budget.getAmount();
            if (current == null) current = BigDecimal.ZERO;
            budget.setRemainingBudget(current.add(delta));
            dailyBudgetRepository.save(budget);
        });
    }
}
