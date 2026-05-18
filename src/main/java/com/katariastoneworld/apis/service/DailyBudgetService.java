package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.DailyBudgetCalculatedSummaryDTO;
import com.katariastoneworld.apis.dto.DailyBudgetEventDTO;
import com.katariastoneworld.apis.dto.DailyBudgetRequestDTO;
import com.katariastoneworld.apis.dto.DailyBudgetStatusDTO;
import com.katariastoneworld.apis.dto.DailyBudgetSummaryDTO;
import com.katariastoneworld.apis.constants.MoneyLedgerCategories;
import com.katariastoneworld.apis.entity.Expense;
import com.katariastoneworld.apis.entity.LedgerPaymentMode;
import com.katariastoneworld.apis.entity.LedgerTransactionType;
import com.katariastoneworld.apis.entity.LoanLedgerEntryType;
import com.katariastoneworld.apis.entity.MoneyCategory;
import com.katariastoneworld.apis.entity.MoneyDirection;
import com.katariastoneworld.apis.entity.MoneyPaymentMode;
import com.katariastoneworld.apis.repository.ExpenseRepository;
import com.katariastoneworld.apis.repository.LoanLedgerEntryRepository;
import com.katariastoneworld.apis.repository.MoneyTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Budget-facing APIs backed by {@code transactions} ({@link BalanceSummaryService}) and {@link ExpenseRepository}.
 */
@Service
@Transactional
public class DailyBudgetService {

    private static final Logger log = LoggerFactory.getLogger(DailyBudgetService.class);

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private static final List<MoneyPaymentMode> IN_HAND_MODES = List.of(MoneyPaymentMode.CASH, MoneyPaymentMode.UPI);
    private static final List<MoneyPaymentMode> BANK_MODES = List.of(MoneyPaymentMode.BANK);
    private static final List<MoneyCategory> NON_EXPENSE_OUT_CATEGORIES = List.of(MoneyCategory.BILL_REVERSAL);

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private BalanceSummaryService balanceSummaryService;

    @Autowired
    private LoanLedgerEntryRepository loanLedgerEntryRepository;

    @Autowired
    private MoneyTransactionRepository moneyTransactionRepository;

    @Autowired
    private MoneyTransactionService moneyTransactionService;

    public List<DailyBudgetSummaryDTO> getAllBudgets() {
        return Collections.emptyList();
    }

    public DailyBudgetStatusDTO getBudgetStatus(String location) {
        return getBudgetStatus(location, LocalDate.now());
    }

    public DailyBudgetStatusDTO getBudgetStatus(String location, LocalDate date) {
        final String loc = location == null ? null : location.trim();
        DailyBudgetStatusDTO dto = new DailyBudgetStatusDTO();
        dto.setLocation(loc);
        dto.setDate(date != null ? date : LocalDate.now());
        if (loc == null || loc.isBlank()) {
            dto.setBudgetAmount(ZERO);
            dto.setSpentAmount(ZERO);
            dto.setRemainingAmount(ZERO);
            return dto;
        }
        LocalDate d = date != null ? date : LocalDate.now();
        List<Expense> dayExpenses = expenseRepository.findByLocationAndDate(loc, d);
        BigDecimal spentAmount = dayExpenses.stream()
                .map(Expense::getAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        dto.setSpentAmount(spentAmount);
        BigDecimal opening = openingInHandBeforeDate(loc, d);
        BigDecimal cashUpiCredits = sumTxDirection(loc, d, d, LedgerTransactionType.CREDIT, IN_HAND_MODES);
        BigDecimal cashUpiDebits = sumOperatingOut(loc, d, d, IN_HAND_MODES);
        BigDecimal billRefunds = sumBillRefundsOut(loc, d, d, IN_HAND_MODES);
        BigDecimal remaining = opening.add(cashUpiCredits).subtract(cashUpiDebits).subtract(billRefunds)
                .setScale(2, RoundingMode.HALF_UP);
        dto.setRemainingAmount(remaining);
        dto.setBudgetAmount(remaining.add(spentAmount).setScale(2, RoundingMode.HALF_UP));
        return dto;
    }

    public DailyBudgetStatusDTO setBudget(String location, DailyBudgetRequestDTO requestDTO) {
        final String loc = location == null ? null : location.trim();
        if (loc == null || loc.isBlank() || requestDTO == null) {
            return getBudgetStatus(loc);
        }
        BigDecimal newAmount = requestDTO.getAmount() != null ? requestDTO.getAmount() : BigDecimal.ZERO;
        newAmount = newAmount.setScale(2, RoundingMode.HALF_UP);
        String fundingSource = requestDTO.getFundingSource() != null
                ? requestDTO.getFundingSource().trim().toUpperCase(Locale.ROOT)
                : "CASH_UPI";
        String adjustmentType = requestDTO.getAdjustmentType() != null
                ? requestDTO.getAdjustmentType().trim().toUpperCase(Locale.ROOT)
                : "INCREASE";
        boolean decreaseAdjustment = "DECREASE".equals(adjustmentType);
        boolean bankTransferFunding = "BANK_TRANSFER".equals(fundingSource);

        BigDecimal appliedDelta = decreaseAdjustment ? newAmount.negate() : newAmount;
        if (appliedDelta.compareTo(BigDecimal.ZERO) != 0) {
            moneyTransactionService.syncFromUnified(
                    loc,
                    LocalDate.now(),
                    appliedDelta.abs(),
                    appliedDelta.signum() > 0 ? LedgerTransactionType.CREDIT : LedgerTransactionType.DEBIT,
                    bankTransferFunding ? LedgerPaymentMode.BANK : LedgerPaymentMode.CASH,
                    "BUDGET_ADJUSTMENT",
                    null,
                    bankTransferFunding
                            ? (appliedDelta.signum() > 0
                                    ? "Manual budget increase via BANK transfer"
                                    : "Manual budget decrease via BANK transfer")
                            : (appliedDelta.signum() > 0
                                    ? "Manual budget increase via CASH/UPI"
                                    : "Manual budget decrease via CASH/UPI"));
        }
        return getBudgetStatus(loc);
    }

    public boolean deleteBudget(String location) {
        return false;
    }

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
            dto.setRemainingAmount(ZERO);
            dto.setExpenseFromEventsInRange(ZERO);
            dto.setRemainingAsOfDate(today);
            dto.setBudgetAmount(ZERO);
            dto.setSpentAmount(ZERO);
            dto.setLoanReceiptsBankChequeInRange(ZERO);
            dto.setLoanRepaymentsBankChequeInRange(ZERO);
            dto.setBankCreditsInRange(ZERO);
            dto.setBankDebitsInRange(ZERO);
            dto.setCashUpiCreditsInRange(ZERO);
            dto.setCashUpiDebitsInRange(ZERO);
            dto.setBankOpeningBalanceCarriedForward(null);
            dto.setBankBalanceIncludingOpening(null);
            return dto;
        }
        BigDecimal expenseSum = balanceSummaryService.sumDebitCashUpiInRange(loc, f, t);
        dto.setExpenseFromEventsInRange(expenseSum.setScale(2, RoundingMode.HALF_UP));

        LocalDate remainingAsOf = t.isAfter(today) ? today : t;
        dto.setRemainingAsOfDate(remainingAsOf);

        BigDecimal openingForDay = openingInHandBeforeDate(loc, remainingAsOf);
        dto.setOpeningBalanceForDay(openingForDay);

        BigDecimal cashUpiCredits = sumTxDirection(loc, f, t, LedgerTransactionType.CREDIT, IN_HAND_MODES);
        BigDecimal cashUpiDebits = sumOperatingOut(loc, f, t, IN_HAND_MODES);
        BigDecimal salesReturnsInRange = sumBillRefundsOut(loc, f, t, IN_HAND_MODES);

        DailyBudgetStatusDTO statusForDay = getBudgetStatus(loc, remainingAsOf);
        BigDecimal spentTbl = statusForDay.getSpentAmount() != null ? statusForDay.getSpentAmount() : BigDecimal.ZERO;
        dto.setSpentAmount(spentTbl.setScale(2, RoundingMode.HALF_UP));

        BigDecimal remaining;
        if (f.equals(t)) {
            remaining = openingForDay.add(cashUpiCredits).subtract(cashUpiDebits).subtract(salesReturnsInRange)
                    .setScale(2, RoundingMode.HALF_UP);
        } else {
            remaining = statusForDay.getRemainingAmount() != null ? statusForDay.getRemainingAmount() : BigDecimal.ZERO;
            remaining = remaining.setScale(2, RoundingMode.HALF_UP);
        }
        dto.setRemainingAmount(remaining);
        dto.setBudgetAmount(remaining.add(spentTbl).setScale(2, RoundingMode.HALF_UP));

        BigDecimal loanRecvBc = loanLedgerEntryRepository.sumBankChequeModeEntriesBetween(loc, f, t, LoanLedgerEntryType.RECEIPT);
        dto.setLoanReceiptsBankChequeInRange((loanRecvBc != null ? loanRecvBc : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));

        BigDecimal loanRepayBc = loanLedgerEntryRepository.sumBankChequeModeEntriesBetween(loc, f, t, LoanLedgerEntryType.REPAYMENT);
        dto.setLoanRepaymentsBankChequeInRange((loanRepayBc != null ? loanRepayBc : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));

        BigDecimal bankCredits = sumTxDirection(loc, f, t, LedgerTransactionType.CREDIT, BANK_MODES);
        BigDecimal bankDebits = sumTxDirection(loc, f, t, LedgerTransactionType.DEBIT, BANK_MODES);

        dto.setBankCreditsInRange(bankCredits.setScale(2, RoundingMode.HALF_UP));
        dto.setBankDebitsInRange(bankDebits.setScale(2, RoundingMode.HALF_UP));
        dto.setCashUpiCreditsInRange(cashUpiCredits.setScale(2, RoundingMode.HALF_UP));
        dto.setCashUpiDebitsInRange(cashUpiDebits.setScale(2, RoundingMode.HALF_UP));
        dto.setBankOpeningBalanceCarriedForward(null);
        dto.setBankBalanceIncludingOpening(null);
        return dto;
    }

    private BigDecimal sumTxDirection(String location, LocalDate from, LocalDate to,
            LedgerTransactionType txnType, List<MoneyPaymentMode> modes) {
        MoneyDirection d = txnType == LedgerTransactionType.CREDIT ? MoneyDirection.IN : MoneyDirection.OUT;
        BigDecimal v = moneyTransactionRepository.sumAmountByLocationDateRangeDirectionModes(
                location, from, to, d, modes);
        return v != null ? v : BigDecimal.ZERO;
    }

    /** Operating cash OUT (excludes bill-cancel refunds — those are revenue reversal, not expense). */
    private BigDecimal sumOperatingOut(String location, LocalDate from, LocalDate to, List<MoneyPaymentMode> modes) {
        BigDecimal v = moneyTransactionRepository.sumAmountByLocationDateRangeDirectionModesExcludingCategories(
                location, from, to, MoneyDirection.OUT, modes, NON_EXPENSE_OUT_CATEGORIES);
        return v != null ? v : BigDecimal.ZERO;
    }

    private BigDecimal sumBillRefundsOut(String location, LocalDate from, LocalDate to, List<MoneyPaymentMode> modes) {
        BigDecimal v = moneyTransactionRepository.sumOutByLocationDateRangeModesAndCategories(
                location, from, to, modes, MoneyLedgerCategories.NON_EXPENSE_OUT);
        return v != null ? v : BigDecimal.ZERO;
    }

    /** CASH+UPI at start of {@code date}, from {@code transactions} only ({@code transaction_date < date}). */
    private BigDecimal openingInHandBeforeDate(String location, LocalDate date) {
        return balanceSummaryService.openingCashUpiAtStartOfDay(location, date);
    }

    public List<DailyBudgetEventDTO> getBudgetEvents(String location, LocalDate from, LocalDate to, int limit) {
        return Collections.emptyList();
    }

    public void adjustRemainingForDailyExpense(String location, BigDecimal delta) {
        // no-op: expenses are recorded in transactions via MoneyTransactionService
    }

    public void recordInHandCollectionFromBill(String location, BigDecimal inHandAmount) {
        // in-hand position comes from transactions (BillService)
    }

    public void recordLoanReceipt(String location, BigDecimal amount, String lenderName, String notes) {
        if (location != null && amount != null) {
            log.debug("loan_receipt location={} amount={}", location.trim(), amount);
        }
    }

    public void recordReceivableRepayment(String location, BigDecimal amount, String borrowerName, String notes) {
        if (location != null && amount != null) {
            log.debug("receivable_repayment location={} amount={}", location.trim(), amount);
        }
    }

    public void adjustBudgetForInHandDelta(String location, BigDecimal delta) {
        // no-op: in-hand position is derived from transactions
    }

    public void recordCashCollectionFromBill(String location, BigDecimal cashAmount) {
        recordInHandCollectionFromBill(location, cashAmount);
    }
}
