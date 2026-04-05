package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.DailyBudgetEventDTO;
import com.katariastoneworld.apis.dto.DailyBudgetRequestDTO;
import com.katariastoneworld.apis.dto.DailyBudgetStatusDTO;
import com.katariastoneworld.apis.dto.DailyBudgetSummaryDTO;
import com.katariastoneworld.apis.entity.DailyBudget;
import com.katariastoneworld.apis.entity.DailyBudgetEvent;
import com.katariastoneworld.apis.entity.Expense;
import com.katariastoneworld.apis.entity.LedgerEntryType;
import com.katariastoneworld.apis.repository.DailyBudgetEventRepository;
import com.katariastoneworld.apis.repository.DailyBudgetRepository;
import com.katariastoneworld.apis.repository.ExpenseRepository;
import com.katariastoneworld.apis.repository.FinancialLedgerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class DailyBudgetService {

    @Autowired
    private DailyBudgetRepository dailyBudgetRepository;

    @Autowired
    private FinancialLedgerRepository financialLedgerRepository;

    @Autowired
    private DailyBudgetEventRepository dailyBudgetEventRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    public List<DailyBudgetSummaryDTO> getAllBudgets() {
        return dailyBudgetRepository.findAll().stream()
                .map(b -> {
                    DailyBudgetSummaryDTO dto = new DailyBudgetSummaryDTO();
                    dto.setId(b.getId());
                    dto.setLocation(b.getLocation());
                    dto.setAmount(b.getAmount());
                    dto.setRemainingBudget(b.getRemainingBudget());
                    LocalDate today = LocalDate.now();
                    dto.setNetLedgerBalance(netLedgerForDate(b.getLocation(), today));
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
     * Expense-based: {@code spentAmount} = sum expenses for the date; {@code remainingAmount} from
     * {@code remaining_budget} when set (after rollover rules for today), else {@code budgetAmount - spentAmount}.
     * Ledger-based (same date): {@code ledgerCreditTotal}, {@code ledgerDebitTotal}, {@code ledgerNetAmount}.
     */
    public DailyBudgetStatusDTO getBudgetStatus(String location, LocalDate date) {
        final String loc = location == null ? null : location.trim();
        LocalDate d = date != null ? date : LocalDate.now();

        BigDecimal budgetAmount = BigDecimal.ZERO;
        BigDecimal storedRemaining = null;
        DailyBudget budget = dailyBudgetRepository.findFirstByLocationAndUserIdIsNull(loc)
                .or(() -> dailyBudgetRepository.findByLocation(loc))
                .orElse(null);
        if (budget != null && budget.getAmount() != null) {
            budgetAmount = budget.getAmount();
            storedRemaining = budget.getRemainingBudget();
        }

        List<Expense> expensesForDate = expenseRepository.findByLocationAndDate(loc, d);
        BigDecimal expenseSpent = expensesForDate.stream()
                .map(Expense::getAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        boolean isToday = d.equals(LocalDate.now());
        if (budget != null && isToday && budget.getUpdatedAt() != null) {
            LocalDate lastUpdatedDate = budget.getUpdatedAt().atZone(ZoneId.systemDefault()).toLocalDate();
            if (lastUpdatedDate.isBefore(LocalDate.now())) {
                BigDecimal yesterdayRemaining = budget.getRemainingBudget() != null
                        ? budget.getRemainingBudget()
                        : budget.getAmount();
                if (yesterdayRemaining == null) {
                    yesterdayRemaining = BigDecimal.ZERO;
                }
                budget.setAmount(yesterdayRemaining);
                budget.setRemainingBudget(yesterdayRemaining.subtract(expenseSpent));
                dailyBudgetRepository.save(budget);

                BigDecimal opening = yesterdayRemaining;
                BigDecimal closing = budget.getRemainingBudget() != null
                        ? budget.getRemainingBudget()
                        : BigDecimal.ZERO;
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

        BigDecimal credits = ledgerCreditsForDate(loc, d);
        BigDecimal debits = ledgerDebitsForDate(loc, d);
        BigDecimal ledgerNet = credits.subtract(debits).setScale(2, RoundingMode.HALF_UP);

        BigDecimal remaining = storedRemaining != null
                ? storedRemaining.setScale(2, RoundingMode.HALF_UP)
                : budgetAmount.subtract(expenseSpent).setScale(2, RoundingMode.HALF_UP);

        DailyBudgetStatusDTO dto = new DailyBudgetStatusDTO();
        dto.setBudgetAmount(budgetAmount.setScale(2, RoundingMode.HALF_UP));
        dto.setSpentAmount(expenseSpent);
        dto.setRemainingAmount(remaining);
        dto.setLedgerCreditTotal(credits);
        dto.setLedgerDebitTotal(debits);
        dto.setLedgerNetAmount(ledgerNet);
        dto.setDate(d);
        dto.setLocation(loc);
        return dto;
    }

    private BigDecimal ledgerCreditsForDate(String location, LocalDate date) {
        if (location == null || location.isBlank()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return financialLedgerRepository
                .sumAmountByLocationAndDateAndEntryType(location.trim(), date, LedgerEntryType.CREDIT)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal ledgerDebitsForDate(String location, LocalDate date) {
        if (location == null || location.isBlank()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return financialLedgerRepository
                .sumAmountByLocationAndDateAndEntryType(location.trim(), date, LedgerEntryType.DEBIT)
                .setScale(2, RoundingMode.HALF_UP);
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

        LocalDate today = LocalDate.now();
        List<Expense> todayExpenses = expenseRepository.findByLocationAndDate(loc, today);
        BigDecimal spentToday = todayExpenses.stream()
                .map(Expense::getAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal openingBefore = BigDecimal.ZERO;
        if (budget == null) {
            budget = new DailyBudget();
            budget.setLocation(loc);
            budget.setUserId(null);
            openingBefore = BigDecimal.ZERO;
        } else {
            openingBefore = budget.getRemainingBudget() != null
                    ? budget.getRemainingBudget()
                    : (budget.getAmount() != null ? budget.getAmount() : BigDecimal.ZERO);
            if (openingBefore == null) {
                openingBefore = BigDecimal.ZERO;
            }
        }
        budget.setAmount(newAmount);
        budget.setRemainingBudget(newAmount.subtract(spentToday));
        dailyBudgetRepository.save(budget);

        BigDecimal closingAfter = budget.getRemainingBudget() != null
                ? budget.getRemainingBudget()
                : BigDecimal.ZERO;
        recordDailyBudgetEvent(
                loc,
                today,
                openingBefore,
                closingAfter,
                closingAfter.subtract(openingBefore),
                "BUDGET_SET"
        );

        return getBudgetStatus(loc);
    }

    public boolean deleteBudget(String location) {
        final String loc = location == null ? null : location.trim();
        return dailyBudgetRepository.findFirstByLocationAndUserIdIsNull(loc)
                .or(() -> dailyBudgetRepository.findByLocation(loc))
                .map(budget -> {
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
                })
                .orElse(false);
    }

    public void adjustRemainingForDailyExpense(String location, BigDecimal delta) {
        if (delta == null || delta.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        final String loc = location == null ? null : location.trim();
        dailyBudgetRepository.findFirstByLocationAndUserIdIsNull(loc)
                .or(() -> dailyBudgetRepository.findByLocation(loc))
                .ifPresent(budget -> {
                    BigDecimal opening = budget.getRemainingBudget() != null
                            ? budget.getRemainingBudget()
                            : (budget.getAmount() != null ? budget.getAmount() : BigDecimal.ZERO);
                    if (opening == null) {
                        opening = BigDecimal.ZERO;
                    }

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

    public List<DailyBudgetEventDTO> getBudgetEvents(String location, LocalDate from, LocalDate to, int limit) {
        String loc = location == null ? null : location.trim();
        if (loc == null || loc.isBlank()) {
            return Collections.emptyList();
        }
        LocalDate f = from != null ? from : LocalDate.now().minusDays(14);
        LocalDate t = to != null ? to : LocalDate.now();
        if (t.isBefore(f)) {
            t = f;
        }

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
        if (location == null || location.isBlank()) {
            return;
        }
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

        recordDailyBudgetEvent(
                loc,
                today,
                openingBalance,
                closingBalance,
                closingBalance.subtract(openingBalance),
                "IN_HAND_COLLECTION"
        );
    }

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

    public void recordCashCollectionFromBill(String location, BigDecimal cashAmount) {
        recordInHandCollectionFromBill(location, cashAmount);
    }
}
