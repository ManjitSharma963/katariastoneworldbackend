package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.DailyBudgetRequestDTO;
import com.katariastoneworld.apis.dto.DailyBudgetStatusDTO;
import com.katariastoneworld.apis.dto.DailyBudgetSummaryDTO;
import com.katariastoneworld.apis.entity.DailyBudget;
import com.katariastoneworld.apis.entity.LedgerEntryType;
import com.katariastoneworld.apis.repository.DailyBudgetRepository;
import com.katariastoneworld.apis.repository.FinancialLedgerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class DailyBudgetService {

    @Autowired
    private DailyBudgetRepository dailyBudgetRepository;

    @Autowired
    private FinancialLedgerRepository financialLedgerRepository;

    public List<DailyBudgetSummaryDTO> getAllBudgets() {
        return dailyBudgetRepository.findAll().stream()
                .map(b -> {
                    DailyBudgetSummaryDTO dto = new DailyBudgetSummaryDTO();
                    dto.setId(b.getId());
                    dto.setLocation(b.getLocation());
                    dto.setAmount(b.getAmount());
                    LocalDate today = LocalDate.now();
                    BigDecimal net = netLedgerForDate(b.getLocation(), today);
                    dto.setNetLedgerBalance(net);
                    dto.setCreatedAt(b.getCreatedAt());
                    dto.setUpdatedAt(b.getUpdatedAt());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public DailyBudgetStatusDTO getBudgetStatus(String location) {
        return getBudgetStatus(location, LocalDate.now());
    }

    /**
     * {@code spentAmount} = sum DEBIT ledger for the date.
     * {@code remainingAmount} = sum CREDIT − sum DEBIT for the date (net inflow).
     * {@code budgetAmount} = optional cap from {@code daily_budget.amount} (informational).
     */
    public DailyBudgetStatusDTO getBudgetStatus(String location, LocalDate date) {
        final String loc = location == null ? null : location.trim();
        BigDecimal budgetAmount = BigDecimal.ZERO;
        DailyBudget budget = dailyBudgetRepository.findFirstByLocationAndUserIdIsNull(loc)
                .or(() -> dailyBudgetRepository.findByLocation(loc))
                .orElse(null);
        if (budget != null && budget.getAmount() != null) {
            budgetAmount = budget.getAmount();
        }

        BigDecimal credits = financialLedgerRepository
                .sumAmountByLocationAndDateAndEntryType(loc, date, LedgerEntryType.CREDIT)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal debits = financialLedgerRepository
                .sumAmountByLocationAndDateAndEntryType(loc, date, LedgerEntryType.DEBIT)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = credits.subtract(debits).setScale(2, RoundingMode.HALF_UP);

        DailyBudgetStatusDTO dto = new DailyBudgetStatusDTO();
        dto.setBudgetAmount(budgetAmount);
        dto.setSpentAmount(debits);
        dto.setRemainingAmount(net);
        dto.setDate(date);
        dto.setLocation(loc);
        return dto;
    }

    private BigDecimal netLedgerForDate(String location, LocalDate date) {
        if (location == null || location.isBlank()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal credits = financialLedgerRepository
                .sumAmountByLocationAndDateAndEntryType(location.trim(), date, LedgerEntryType.CREDIT);
        BigDecimal debits = financialLedgerRepository
                .sumAmountByLocationAndDateAndEntryType(location.trim(), date, LedgerEntryType.DEBIT);
        return credits.subtract(debits).setScale(2, RoundingMode.HALF_UP);
    }

    public DailyBudgetStatusDTO setBudget(String location, DailyBudgetRequestDTO requestDTO) {
        final String loc = location == null ? null : location.trim();
        BigDecimal newAmount = requestDTO.getAmount() != null ? requestDTO.getAmount() : BigDecimal.ZERO;
        DailyBudget budget = dailyBudgetRepository.findFirstByLocationAndUserIdIsNull(loc)
                .or(() -> dailyBudgetRepository.findByLocation(loc))
                .orElse(null);
        if (budget == null) {
            budget = new DailyBudget();
            budget.setLocation(loc);
            budget.setAmount(newAmount);
        } else {
            budget.setAmount(newAmount);
        }
        dailyBudgetRepository.save(budget);
        return getBudgetStatus(loc);
    }

    public boolean deleteBudget(String location) {
        final String loc = location == null ? null : location.trim();
        return dailyBudgetRepository.findFirstByLocationAndUserIdIsNull(loc)
                .or(() -> dailyBudgetRepository.findByLocation(loc))
                .map(budget -> {
                    dailyBudgetRepository.delete(budget);
                    return true;
                })
                .orElse(false);
    }
}
