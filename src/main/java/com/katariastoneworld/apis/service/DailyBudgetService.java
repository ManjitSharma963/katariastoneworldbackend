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
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
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

    /** Get daily budget status for the location. Location-scoped. */
    public DailyBudgetStatusDTO getBudgetStatus(String location) {
        return getBudgetStatus(location, LocalDate.now());
    }

    public DailyBudgetStatusDTO getBudgetStatus(String location, LocalDate date) {
        BigDecimal budgetAmount = BigDecimal.ZERO;
        BigDecimal storedRemaining = null;
        // Prefer location-scoped row (user_id NULL) so we use the same row as in DB / UI
        DailyBudget budget = dailyBudgetRepository.findFirstByLocationAndUserIdIsNull(location)
                .or(() -> dailyBudgetRepository.findByLocation(location))
                .orElse(null);
        if (budget != null && budget.getAmount() != null) {
            budgetAmount = budget.getAmount();
            storedRemaining = budget.getRemainingBudget();
        }
        List<Expense> todayExpenses = expenseRepository.findByLocationAndDate(location, date);
        BigDecimal spentAmount = todayExpenses.stream()
                .map(Expense::getAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean isToday = date.equals(LocalDate.now());

        // Roll-over: yesterday's remaining in hand becomes today's budget (when first opening/using budget for the new day)
        if (budget != null && isToday && budget.getUpdatedAt() != null) {
            LocalDate lastUpdatedDate = budget.getUpdatedAt().atZone(ZoneId.systemDefault()).toLocalDate();
            if (lastUpdatedDate.isBefore(LocalDate.now())) {
                BigDecimal yesterdayRemaining = budget.getRemainingBudget() != null ? budget.getRemainingBudget() : budget.getAmount();
                if (yesterdayRemaining == null) yesterdayRemaining = BigDecimal.ZERO;
                budget.setAmount(yesterdayRemaining);
                budget.setRemainingBudget(yesterdayRemaining.subtract(spentAmount));
                dailyBudgetRepository.save(budget);
                budgetAmount = yesterdayRemaining;
                storedRemaining = budget.getRemainingBudget();
            }
        }

        BigDecimal computedRemaining = budgetAmount.subtract(spentAmount);
        // For today, always use stored remaining_budget when present (source of truth in DB)
        BigDecimal remainingAmount;
        if (isToday && storedRemaining != null) {
            remainingAmount = storedRemaining;
        } else {
            remainingAmount = computedRemaining;
        }
        if (budget != null && isToday && budget.getRemainingBudget() == null && budget.getAmount() != null) {
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

    public DailyBudgetStatusDTO setBudget(String location, DailyBudgetRequestDTO requestDTO) {
        BigDecimal newAmount = requestDTO.getAmount() != null ? requestDTO.getAmount() : BigDecimal.ZERO;
        DailyBudget budget = dailyBudgetRepository.findFirstByLocationAndUserIdIsNull(location)
                .or(() -> dailyBudgetRepository.findByLocation(location))
                .orElse(null);
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

    public boolean deleteBudget(String location) {
        return dailyBudgetRepository.findFirstByLocationAndUserIdIsNull(location)
                .or(() -> dailyBudgetRepository.findByLocation(location))
                .map(budget -> {
                    dailyBudgetRepository.delete(budget);
                    return true;
                }).orElse(false);
    }

    public void adjustRemainingForDailyExpense(String location, BigDecimal delta) {
        if (delta == null || delta.compareTo(BigDecimal.ZERO) == 0) return;
        dailyBudgetRepository.findFirstByLocationAndUserIdIsNull(location)
                .or(() -> dailyBudgetRepository.findByLocation(location))
                .ifPresent(budget -> {
            BigDecimal current = budget.getRemainingBudget() != null ? budget.getRemainingBudget() : budget.getAmount();
            if (current == null) current = BigDecimal.ZERO;
            budget.setRemainingBudget(current.add(delta));
            dailyBudgetRepository.save(budget);
        });
    }
}
