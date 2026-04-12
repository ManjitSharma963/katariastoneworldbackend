package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.DailyBudgetRequestDTO;
import com.katariastoneworld.apis.dto.DailyBudgetStatusDTO;
import com.katariastoneworld.apis.dto.DailyBudgetEventDTO;
import com.katariastoneworld.apis.dto.DailyBudgetCalculatedSummaryDTO;
import com.katariastoneworld.apis.entity.BillPaymentMode;
import com.katariastoneworld.apis.entity.DailyBudget;
import com.katariastoneworld.apis.entity.DailyBudgetEvent;
import com.katariastoneworld.apis.entity.EmployeePayrollLedgerEntry;
import com.katariastoneworld.apis.entity.Expense;
import com.katariastoneworld.apis.entity.FinancialLedgerEntry;
import com.katariastoneworld.apis.repository.DailyBudgetRepository;
import com.katariastoneworld.apis.repository.DailyBudgetEventRepository;
import com.katariastoneworld.apis.entity.LoanLedgerEntryType;
import com.katariastoneworld.apis.repository.ExpenseRepository;
import com.katariastoneworld.apis.repository.EmployeePayrollLedgerRepository;
import com.katariastoneworld.apis.repository.FinancialLedgerRepository;
import com.katariastoneworld.apis.repository.LoanLedgerEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Service
@Transactional
public class DailyBudgetService {

    private static final Logger log = LoggerFactory.getLogger(DailyBudgetService.class);

    @Autowired
    private DailyBudgetRepository dailyBudgetRepository;

    @Autowired
    private DailyBudgetEventRepository dailyBudgetEventRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private LoanLedgerEntryRepository loanLedgerEntryRepository;

    @Autowired
    private FinancialLedgerRepository financialLedgerRepository;

    @Autowired
    private EmployeePayrollLedgerRepository employeePayrollLedgerRepository;

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

                // Bank channel: carry opening through each completed calendar day (same net rules as Expenses "Amount in bank").
                BigDecimal bankOpen = budget.getBankOpeningBalance() != null
                        ? budget.getBankOpeningBalance().setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                LocalDate d = lastUpdatedDate;
                while (d.isBefore(LocalDate.now())) {
                    bankOpen = bankOpen.add(computeBankNetForLocationAndDateRange(loc, d, d)).setScale(2, RoundingMode.HALF_UP);
                    d = d.plusDays(1);
                }
                budget.setBankOpeningBalance(bankOpen);

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
        remainingAmount = alignTodayRemainingWithEventLedgerIfNeeded(loc, date, budget, remainingAmount);
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
     * Server-side figures for a date range: remaining as-of (today or last event on a past day) and
     * sum of expense ledger events in range. Use this instead of replaying capped event lists in the UI.
     */
    public DailyBudgetCalculatedSummaryDTO getCalculatedSummary(String location, LocalDate from, LocalDate to) {
        final String loc = location == null ? null : location.trim();
        final LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate f = from != null ? from : (to != null ? to : today);
        LocalDate t = to != null ? to : f;
        if (t.isBefore(f)) {
            LocalDate swap = f;
            f = t;
            t = swap;
        }
        DailyBudgetCalculatedSummaryDTO dto = new DailyBudgetCalculatedSummaryDTO();
        dto.setLocation(loc);
        dto.setFrom(f);
        dto.setTo(t);
        if (loc == null || loc.isBlank()) {
            dto.setRemainingAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            dto.setExpenseFromEventsInRange(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            dto.setRemainingAsOfDate(today);
            dto.setBudgetAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            dto.setSpentAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            dto.setLoanReceiptsBankChequeInRange(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            dto.setLoanRepaymentsBankChequeInRange(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            dto.setBankCreditsInRange(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            dto.setBankDebitsInRange(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            dto.setCashUpiCreditsInRange(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            dto.setCashUpiDebitsInRange(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            dto.setBankOpeningBalanceCarriedForward(null);
            dto.setBankBalanceIncludingOpening(null);
            return dto;
        }
        BigDecimal expenseSum = dailyBudgetEventRepository.sumExpenseSpentFromEvents(loc, f, t);
        if (expenseSum == null) {
            expenseSum = BigDecimal.ZERO;
        }
        if (expenseSum.compareTo(BigDecimal.ZERO) < 0) {
            expenseSum = BigDecimal.ZERO;
        }
        dto.setExpenseFromEventsInRange(expenseSum.setScale(2, RoundingMode.HALF_UP));

        LocalDate remainingAsOf = t.isAfter(today) ? today : t;
        dto.setRemainingAsOfDate(remainingAsOf);

        DailyBudgetStatusDTO statusForDay = getBudgetStatus(loc, remainingAsOf);
        BigDecimal budgetAmt = statusForDay.getBudgetAmount() != null ? statusForDay.getBudgetAmount() : BigDecimal.ZERO;
        BigDecimal spentTbl = statusForDay.getSpentAmount() != null ? statusForDay.getSpentAmount() : BigDecimal.ZERO;
        dto.setBudgetAmount(budgetAmt.setScale(2, RoundingMode.HALF_UP));
        dto.setSpentAmount(spentTbl.setScale(2, RoundingMode.HALF_UP));

        dailyBudgetEventRepository.findFirstByLocationAndDateOrderByCreatedAtAsc(loc, remainingAsOf)
                .map(DailyBudgetEvent::getOpeningBalance)
                .ifPresent(o -> dto.setOpeningBalanceForDay(o.setScale(2, RoundingMode.HALF_UP)));

        BigDecimal remaining;
        if (remainingAsOf.equals(today)) {
            remaining = statusForDay.getRemainingAmount() != null ? statusForDay.getRemainingAmount() : BigDecimal.ZERO;
        } else {
            remaining = dailyBudgetEventRepository
                    .findFirstByLocationAndDateOrderByCreatedAtDesc(loc, remainingAsOf)
                    .map(DailyBudgetEvent::getClosingBalance)
                    .orElse(BigDecimal.ZERO);
        }
        if (remaining == null) {
            remaining = BigDecimal.ZERO;
        }
        dto.setRemainingAmount(remaining.setScale(2, RoundingMode.HALF_UP));

        BigDecimal loanRecvBc = computeBankLoanReceiptsInRange(loc, f, t);
        dto.setLoanReceiptsBankChequeInRange(loanRecvBc.setScale(2, RoundingMode.HALF_UP));

        BigDecimal loanRepayBc = loanLedgerEntryRepository.sumBankChequeModeEntriesBetween(loc, f, t, LoanLedgerEntryType.REPAYMENT);
        if (loanRepayBc == null) {
            loanRepayBc = BigDecimal.ZERO;
        }
        dto.setLoanRepaymentsBankChequeInRange(loanRepayBc.setScale(2, RoundingMode.HALF_UP));

        List<BillPaymentMode> inHandModes = List.of(BillPaymentMode.CASH, BillPaymentMode.UPI);
        List<FinancialLedgerEntry.EventType> ledgerCreditTypes = List.of(
                FinancialLedgerEntry.EventType.CLIENT_PAYMENT_IN,
                FinancialLedgerEntry.EventType.ADVANCE_DEPOSIT);
        List<FinancialLedgerEntry.EventType> billPaymentTypes = List.of(FinancialLedgerEntry.EventType.BILL_PAYMENT);

        BigDecimal ledgerCashUpiCredit = nullToZero(financialLedgerRepository.sumAmountByLocationDateRangeTypesAndModes(
                loc, f, t, ledgerCreditTypes, inHandModes));

        /*
         * BILL_PAYMENT rows come from customer sales invoices (GST / Non-GST): money collected from customers.
         * They are inflows for the chosen payment mode — not supplier payables. Count as credits, not debits.
         */
        BigDecimal ledgerCashUpiFromCustomerBills = nullToZero(financialLedgerRepository.sumAmountByLocationDateRangeTypesAndModes(
                loc, f, t, billPaymentTypes, inHandModes));

        BigDecimal expenseCashUpiOut = sumExpenseOutflowsForPaymentChannel(loc, f, t, ExpensePayChannel.CASH_UPI);

        List<EmployeePayrollLedgerEntry.EventType> payrollOutflowTypes = List.of(
                EmployeePayrollLedgerEntry.EventType.SALARY_CASH_PAID,
                EmployeePayrollLedgerEntry.EventType.ADVANCE_GIVEN);
        BigDecimal payrollCashUpiOut = nullToZero(employeePayrollLedgerRepository.sumAmountByLocationDateRangeTypesAndModes(
                loc, f, t, payrollOutflowTypes, inHandModes));

        BigDecimal loanCashUpiRecv = nullToZero(loanLedgerEntryRepository.sumCashUpiReceiptsBetween(
                loc, f, t, LoanLedgerEntryType.RECEIPT));

        BigDecimal bankCredits = computeBankCreditsInRange(loc, f, t);
        BigDecimal bankDebits = computeBankDebitsInRange(loc, f, t);
        BigDecimal cashUpiCredits = loanCashUpiRecv.add(ledgerCashUpiCredit).add(ledgerCashUpiFromCustomerBills);
        BigDecimal cashUpiDebits = expenseCashUpiOut.add(payrollCashUpiOut);

        dto.setBankCreditsInRange(bankCredits.setScale(2, RoundingMode.HALF_UP));
        dto.setBankDebitsInRange(bankDebits.setScale(2, RoundingMode.HALF_UP));
        dto.setCashUpiCreditsInRange(cashUpiCredits.setScale(2, RoundingMode.HALF_UP));
        dto.setCashUpiDebitsInRange(cashUpiDebits.setScale(2, RoundingMode.HALF_UP));

        dto.setBankOpeningBalanceCarriedForward(null);
        dto.setBankBalanceIncludingOpening(null);
        if (f.equals(t) && t.equals(today)) {
            BigDecimal bankOpening = dailyBudgetRepository.findFirstByLocationAndUserIdIsNull(loc)
                    .or(() -> dailyBudgetRepository.findByLocation(loc))
                    .map(b -> b.getBankOpeningBalance() != null
                            ? b.getBankOpeningBalance().setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .orElse(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            dto.setBankOpeningBalanceCarriedForward(bankOpening);
            BigDecimal net = bankCredits.subtract(bankDebits).setScale(2, RoundingMode.HALF_UP);
            dto.setBankBalanceIncludingOpening(bankOpening.add(net).setScale(2, RoundingMode.HALF_UP));
        }
        return dto;
    }

    private BigDecimal computeBankLoanReceiptsInRange(String loc, LocalDate f, LocalDate t) {
        BigDecimal loanRecvBc = loanLedgerEntryRepository.sumBankChequeModeEntriesBetween(loc, f, t, LoanLedgerEntryType.RECEIPT);
        return loanRecvBc != null ? loanRecvBc : BigDecimal.ZERO;
    }

    /**
     * Bank-channel credits: loan (bank/cheque), financial ledger client/advance in bank modes, customer bill payments in bank modes.
     */
    private BigDecimal computeBankCreditsInRange(String loc, LocalDate f, LocalDate t) {
        BigDecimal loanRecvBc = computeBankLoanReceiptsInRange(loc, f, t);
        List<BillPaymentMode> bankModes = List.of(BillPaymentMode.BANK_TRANSFER, BillPaymentMode.CHEQUE, BillPaymentMode.OTHER);
        List<FinancialLedgerEntry.EventType> ledgerCreditTypes = List.of(
                FinancialLedgerEntry.EventType.CLIENT_PAYMENT_IN,
                FinancialLedgerEntry.EventType.ADVANCE_DEPOSIT);
        List<FinancialLedgerEntry.EventType> billPaymentTypes = List.of(FinancialLedgerEntry.EventType.BILL_PAYMENT);
        BigDecimal ledgerBankCredit = nullToZero(financialLedgerRepository.sumAmountByLocationDateRangeTypesAndModes(
                loc, f, t, ledgerCreditTypes, bankModes));
        BigDecimal ledgerBankFromCustomerBills = nullToZero(financialLedgerRepository.sumAmountByLocationDateRangeTypesAndModes(
                loc, f, t, billPaymentTypes, bankModes));
        return loanRecvBc.add(ledgerBankCredit).add(ledgerBankFromCustomerBills).setScale(2, RoundingMode.HALF_UP);
    }

    /** Bank-channel debits: expenses and payroll outflows paid bank/cheque/card. */
    private BigDecimal computeBankDebitsInRange(String loc, LocalDate f, LocalDate t) {
        List<BillPaymentMode> bankModes = List.of(BillPaymentMode.BANK_TRANSFER, BillPaymentMode.CHEQUE, BillPaymentMode.OTHER);
        List<EmployeePayrollLedgerEntry.EventType> payrollOutflowTypes = List.of(
                EmployeePayrollLedgerEntry.EventType.SALARY_CASH_PAID,
                EmployeePayrollLedgerEntry.EventType.ADVANCE_GIVEN);
        BigDecimal expenseBankOut = sumExpenseOutflowsForPaymentChannel(loc, f, t, ExpensePayChannel.BANK_CARD_CHEQUE);
        BigDecimal payrollBankOut = nullToZero(employeePayrollLedgerRepository.sumAmountByLocationDateRangeTypesAndModes(
                loc, f, t, payrollOutflowTypes, bankModes));
        return expenseBankOut.add(payrollBankOut).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal computeBankNetForLocationAndDateRange(String loc, LocalDate f, LocalDate t) {
        return computeBankCreditsInRange(loc, f, t).subtract(computeBankDebitsInRange(loc, f, t)).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private enum ExpensePayChannel {
        CASH_UPI,
        BANK_CARD_CHEQUE
    }

    private static ExpensePayChannel classifyExpensePayment(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            return null;
        }
        String v = paymentMethod.trim().toLowerCase(Locale.ROOT).replace('-', ' ');
        if ("cash".equals(v) || "upi".equals(v)) {
            return ExpensePayChannel.CASH_UPI;
        }
        if (v.contains("bank") || "card".equals(v) || "cheque".equals(v) || "check".equals(v)) {
            return ExpensePayChannel.BANK_CARD_CHEQUE;
        }
        return null;
    }

    private BigDecimal sumExpenseOutflowsForPaymentChannel(String location, LocalDate from, LocalDate to,
            ExpensePayChannel channel) {
        List<Expense> rows = expenseRepository.findByLocationAndDateBetween(location, from, to);
        BigDecimal sum = BigDecimal.ZERO;
        for (Expense e : rows) {
            if (e.getAmount() == null) {
                continue;
            }
            if (classifyExpensePayment(e.getPaymentMethod()) != channel) {
                continue;
            }
            sum = sum.add(e.getAmount());
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
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

    /**
     * If {@code daily_budget.remaining_budget} reads as 0 (or there is no row) but today's latest
     * {@code daily_budget_events} closing balance is still positive, use the event — matches Budget history UI.
     * When a row exists, repairs {@code remaining_budget} so the card and modal stay in sync.
     */
    private BigDecimal alignTodayRemainingWithEventLedgerIfNeeded(
            String loc, LocalDate date, DailyBudget budget, BigDecimal remainingAmount) {
        if (loc == null || loc.isBlank()) {
            return remainingAmount;
        }
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        if (!date.equals(today)) {
            return remainingAmount;
        }
        BigDecimal rem = remainingAmount != null ? remainingAmount.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        Optional<DailyBudgetEvent> opt = dailyBudgetEventRepository.findFirstByLocationAndDateOrderByCreatedAtDesc(loc, date);
        if (opt.isEmpty() || opt.get().getClosingBalance() == null) {
            return rem;
        }
        BigDecimal eventClosing = opt.get().getClosingBalance().setScale(2, RoundingMode.HALF_UP);
        if (eventClosing.compareTo(BigDecimal.ZERO) <= 0) {
            return rem;
        }
        boolean rowShowsNoCash = rem.compareTo(BigDecimal.ZERO) == 0;
        boolean noRowButLedgerHasCash = budget == null && rem.compareTo(eventClosing) < 0;
        if (!rowShowsNoCash && !noRowButLedgerHasCash) {
            return rem;
        }
        if (budget != null) {
            budget.setRemainingBudget(eventClosing);
            dailyBudgetRepository.save(budget);
        }
        return eventClosing;
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
        addInHandCashToBudget(location.trim(), inHandAmount.setScale(2, RoundingMode.HALF_UP), "IN_HAND_COLLECTION");
    }

    /**
     * Cash borrowed (market / financier / personal loan draw). Increases today's budget amount and remaining,
     * and records {@code LOAN_RECEIVED} in daily_budget_events (Budget history shows as a credit).
     */
    public void recordLoanReceipt(String location, BigDecimal amount, String lenderName, String notes) {
        if (location == null || location.isBlank() || amount == null
                || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String loc = location.trim();
        BigDecimal add = amount.setScale(2, RoundingMode.HALF_UP);
        log.info("loan_receipt location={} amount={} lender={} notes={}",
                loc, add, lenderName != null ? lenderName.trim() : "", notes != null ? notes.trim() : "");
        addInHandCashToBudget(loc, add, "LOAN_RECEIVED");
    }

    /**
     * Shared path: positive cash inflow to daily budget (sales in-hand, loan draw, etc.).
     */
    private void addInHandCashToBudget(String loc, BigDecimal add, String eventType) {
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
                eventType != null ? eventType : "IN_HAND_COLLECTION"
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
