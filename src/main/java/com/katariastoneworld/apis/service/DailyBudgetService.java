package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.DailyBudgetRequestDTO;
import com.katariastoneworld.apis.dto.DailyBudgetStatusDTO;
import com.katariastoneworld.apis.dto.DailyBudgetEventDTO;
import com.katariastoneworld.apis.entity.DailyBudget;
import com.katariastoneworld.apis.entity.DailyBudgetEvent;
import com.katariastoneworld.apis.entity.Expense;
import com.katariastoneworld.apis.repository.DailyBudgetRepository;
import com.katariastoneworld.apis.repository.DailyBudgetEventRepository;
import com.katariastoneworld.apis.repository.ExpenseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.katariastoneworld.apis.dto.DailyBudgetSummaryDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Service
@Transactional
public class DailyBudgetService {

    @Autowired
    private DailyBudgetRepository dailyBudgetRepository;

    @Autowired
    private DailyBudgetEventRepository dailyBudgetEventRepository;

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
        final String loc = location == null ? null : location.trim();
        BigDecimal budgetAmount = BigDecimal.ZERO;
        BigDecimal storedRemaining = null;
        // Prefer location-scoped row (user_id NULL) so we use the same row as in DB / UI
        DailyBudget budget = dailyBudgetRepository.findFirstByLocationAndUserIdIsNull(loc)
                .or(() -> dailyBudgetRepository.findByLocation(loc))
                .orElse(null);
        if (budget != null && budget.getAmount() != null) {
            budgetAmount = budget.getAmount();
            storedRemaining = budget.getRemainingBudget();
        }
        List<Expense> todayExpenses = expenseRepository.findByLocationAndDate(loc, date);
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

                // Log rollover so UI can show opening/closing balance for the new day.
                BigDecimal opening = yesterdayRemaining != null ? yesterdayRemaining : BigDecimal.ZERO;
                BigDecimal closing = budget.getRemainingBudget() != null ? budget.getRemainingBudget() : BigDecimal.ZERO;
                recordDailyBudgetEvent(
                        loc,
                        LocalDate.now(),
                        opening,
                        closing,
                        closing.subtract(opening),
                        "ROLL_OVER"
                );

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
        dto.setLocation(loc);
        return dto;
    }

    public DailyBudgetStatusDTO setBudget(String location, DailyBudgetRequestDTO requestDTO) {
        final String loc = location == null ? null : location.trim();
        BigDecimal newAmount = requestDTO.getAmount() != null ? requestDTO.getAmount() : BigDecimal.ZERO;
        DailyBudget budget = dailyBudgetRepository.findFirstByLocationAndUserIdIsNull(loc)
                .or(() -> dailyBudgetRepository.findByLocation(loc))
                .orElse(null);

        // Compute today's spent before setting the new amount so opening/closing are correct.
        LocalDate today = LocalDate.now();
        List<Expense> todayExpenses = expenseRepository.findByLocationAndDate(loc, today);
        BigDecimal spentToday = todayExpenses.stream()
                .map(Expense::getAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (budget == null) {
            budget = new DailyBudget();
            budget.setLocation(loc);
            budget.setAmount(newAmount);
            budget.setRemainingBudget(newAmount.subtract(spentToday));
        } else {
            budget.setAmount(newAmount);
            budget.setRemainingBudget(newAmount.subtract(spentToday));
        }
        dailyBudgetRepository.save(budget);

        // Event for "change of daily budget"
        BigDecimal opening = budget.getAmount() != null ? budget.getAmount() : BigDecimal.ZERO;
        BigDecimal closing = budget.getRemainingBudget() != null ? budget.getRemainingBudget() : BigDecimal.ZERO;
        recordDailyBudgetEvent(
                loc,
                today,
                opening,
                closing,
                closing.subtract(opening), // delta (closing - opening)
                "BUDGET_SET"
        );

        return getBudgetStatus(loc);
    }

    public boolean deleteBudget(String location) {
        final String loc = location == null ? null : location.trim();
        return dailyBudgetRepository.findFirstByLocationAndUserIdIsNull(loc)
                .or(() -> dailyBudgetRepository.findByLocation(loc))
                .map(budget -> {
                    // Best-effort: record that budget was cleared for today.
                    LocalDate today = LocalDate.now();
                    BigDecimal opening = budget.getAmount() != null ? budget.getAmount() : BigDecimal.ZERO;
                    BigDecimal closing = budget.getRemainingBudget() != null ? budget.getRemainingBudget() : BigDecimal.ZERO;
                    recordDailyBudgetEvent(
                            loc,
                            today,
                            opening,
                            closing,
                            closing.subtract(opening),
                            "BUDGET_CLEARED"
                    );
                    dailyBudgetRepository.delete(budget);
                    return true;
                }).orElse(false);
    }

    public void adjustRemainingForDailyExpense(String location, BigDecimal delta) {
        if (delta == null || delta.compareTo(BigDecimal.ZERO) == 0) return;
        dailyBudgetRepository.findFirstByLocationAndUserIdIsNull(location)
                .or(() -> dailyBudgetRepository.findByLocation(location))
                .ifPresent(budget -> {
            BigDecimal opening = budget.getRemainingBudget() != null
                    ? budget.getRemainingBudget()
                    : (budget.getAmount() != null ? budget.getAmount() : BigDecimal.ZERO);
            if (opening == null) opening = BigDecimal.ZERO;

            BigDecimal closing = opening.add(delta);
            budget.setRemainingBudget(closing);
            dailyBudgetRepository.save(budget);

            LocalDate today = LocalDate.now();
            String eventType = delta.signum() < 0 ? "EXPENSE_DEBIT" : "EXPENSE_CREDIT";
            recordDailyBudgetEvent(
                    budget.getLocation(),
                    today,
                    opening,
                    closing,
                    closing.subtract(opening),
                    eventType
            );
        });
    }

    /**
     * Returns daily budget event history for the location scoped by JWT.
     */
    public List<DailyBudgetEventDTO> getBudgetEvents(String location, LocalDate from, LocalDate to, int limit) {
        String loc = location == null ? null : location.trim();
        if (loc == null || loc.isBlank()) return Collections.emptyList();
        LocalDate f = from != null ? from : LocalDate.now().minusDays(14);
        LocalDate t = to != null ? to : LocalDate.now();
        if (t.isBefore(f)) t = f;

        Pageable pageable = PageRequest.of(0, Math.max(1, limit), Sort.by(Sort.Direction.DESC, "createdAt"));
        List<DailyBudgetEvent> rows = dailyBudgetEventRepository.findByLocationAndDateBetweenOrderByCreatedAtDesc(loc, f, t, pageable);
        return rows.stream().map(e -> {
            DailyBudgetEventDTO dto = new DailyBudgetEventDTO();
            dto.setId(e.getId());
            dto.setLocation(e.getLocation());
            dto.setDate(e.getDate());
            dto.setOpeningBalance(e.getOpeningBalance());
            dto.setClosingBalance(e.getClosingBalance());
            dto.setSpentAmount(e.getSpentAmount());
            dto.setDelta(e.getDelta());
            dto.setEventType(e.getEventType());
            dto.setCreatedAt(e.getCreatedAt());
            return dto;
        }).collect(Collectors.toList());
    }

    private void recordDailyBudgetEvent(String location,
                                          LocalDate date,
                                          BigDecimal opening,
                                          BigDecimal closing,
                                          BigDecimal delta) {
        recordDailyBudgetEvent(location, date, opening, closing, delta, "BUDGET_UPDATE");
    }

    private void recordDailyBudgetEvent(String location,
                                          LocalDate date,
                                          BigDecimal opening,
                                          BigDecimal closing,
                                          BigDecimal delta,
                                          String eventType) {
        if (location == null || location.isBlank()) return;
        LocalDate d = date != null ? date : LocalDate.now();

        BigDecimal o = opening != null ? opening : BigDecimal.ZERO;
        BigDecimal c = closing != null ? closing : BigDecimal.ZERO;
        BigDecimal spent = o.subtract(c);
        DailyBudgetEvent evt = new DailyBudgetEvent();
        evt.setLocation(location.trim());
        evt.setDate(d);
        evt.setOpeningBalance(o.setScale(2, RoundingMode.HALF_UP));
        evt.setClosingBalance(c.setScale(2, RoundingMode.HALF_UP));
        evt.setSpentAmount(spent.setScale(2, RoundingMode.HALF_UP));
        evt.setDelta(delta != null ? delta.setScale(2, RoundingMode.HALF_UP) : null);
        evt.setEventType(eventType != null ? eventType : "BUDGET_UPDATE");
        dailyBudgetEventRepository.save(evt);
    }

    /**
     * When a bill is collected, add in-hand collections to today's budget and remaining
     * for the location. "In-hand" currently includes CASH + UPI.
     * Creates a daily_budget row for the location if none exists (so the UI can show updates).
     */
    public void recordInHandCollectionFromBill(String location, BigDecimal inHandAmount) {
        if (location == null || location.isBlank() || inHandAmount == null
                || inHandAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String loc = location.trim();
        BigDecimal add = inHandAmount.setScale(2, RoundingMode.HALF_UP);
        DailyBudget budget = dailyBudgetRepository.findFirstByLocationAndUserIdIsNull(loc)
                .or(() -> dailyBudgetRepository.findByLocation(loc))
                .orElse(null);
        LocalDate today = LocalDate.now();
        BigDecimal openingBalance;
        BigDecimal closingBalance;

        if (budget == null) {
            budget = new DailyBudget();
            budget.setLocation(loc);
            budget.setUserId(null);
            budget.setAmount(add);
            budget.setRemainingBudget(add);
            openingBalance = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            closingBalance = add;
        } else {
            BigDecimal amt = budget.getAmount() != null ? budget.getAmount() : BigDecimal.ZERO;
            BigDecimal rem = budget.getRemainingBudget() != null ? budget.getRemainingBudget() : amt;
            openingBalance = rem != null ? rem : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            budget.setAmount(amt.add(add));
            budget.setRemainingBudget(openingBalance.add(add));
            closingBalance = openingBalance.add(add);
        }
        dailyBudgetRepository.save(budget);

        // Log so Budget history shows cash/upi collections as CREDIT.
        recordDailyBudgetEvent(
                loc,
                today,
                openingBalance,
                closingBalance,
                closingBalance.subtract(openingBalance),
                "IN_HAND_COLLECTION"
        );
    }

    /**
     * Deterministic adjustment for payment edit/delete flows.
     * Positive delta increases in-hand; negative delta reverses previously counted in-hand.
     */
    public void adjustBudgetForInHandDelta(String location, BigDecimal delta) {
        if (location == null || location.isBlank() || delta == null || delta.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        String loc = location.trim();
        BigDecimal add = delta.setScale(2, RoundingMode.HALF_UP);
        DailyBudget budget = dailyBudgetRepository.findFirstByLocationAndUserIdIsNull(loc)
                .or(() -> dailyBudgetRepository.findByLocation(loc))
                .orElse(null);
        LocalDate today = LocalDate.now();
        BigDecimal openingBalance;
        BigDecimal closingBalance;
        if (budget == null) {
            budget = new DailyBudget();
            budget.setLocation(loc);
            budget.setUserId(null);
            budget.setAmount(add.max(BigDecimal.ZERO));
            budget.setRemainingBudget(add.max(BigDecimal.ZERO));
            dailyBudgetRepository.save(budget);

            openingBalance = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            closingBalance = budget.getRemainingBudget() != null ? budget.getRemainingBudget() : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            if (closingBalance.compareTo(openingBalance) != 0) {
                recordDailyBudgetEvent(
                        loc,
                        today,
                        openingBalance,
                        closingBalance,
                        closingBalance.subtract(openingBalance),
                        "IN_HAND_COLLECTION_ADJUSTMENT"
                );
            }
            return;
        }
        BigDecimal amt = budget.getAmount() != null ? budget.getAmount() : BigDecimal.ZERO;
        BigDecimal rem = budget.getRemainingBudget() != null ? budget.getRemainingBudget() : amt;
        openingBalance = rem != null ? rem : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        budget.setAmount(amt.add(add));
        budget.setRemainingBudget(openingBalance.add(add));
        closingBalance = openingBalance.add(add);
        dailyBudgetRepository.save(budget);

        // Log so Budget history shows CASH/UPI adjustments as CREDIT/DEBIT.
        if (closingBalance.compareTo(openingBalance) != 0) {
            recordDailyBudgetEvent(
                    loc,
                    today,
                    openingBalance,
                    closingBalance,
                    closingBalance.subtract(openingBalance),
                    add.signum() < 0 ? "IN_HAND_DECREASE" : "IN_HAND_INCREASE"
            );
        }
    }

    /** Backward compatible alias (legacy call sites). */
    public void recordCashCollectionFromBill(String location, BigDecimal cashAmount) {
        recordInHandCollectionFromBill(location, cashAmount);
    }
}
