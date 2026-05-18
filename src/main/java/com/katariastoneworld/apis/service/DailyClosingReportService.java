package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.DailyClosingBillLineDTO;
import com.katariastoneworld.apis.dto.DailyCashFlowDTO;
import com.katariastoneworld.apis.dto.DailyClosingExpenseLineDTO;
import com.katariastoneworld.apis.dto.DailyClosingReportDTO;
import com.katariastoneworld.apis.dto.PaymentModeTotalsDTO;
import com.katariastoneworld.apis.dto.ReconciliationCauseDTO;
import com.katariastoneworld.apis.dto.ReconciliationReportDTO;
import com.katariastoneworld.apis.dto.SalesChargesSummaryDTO;
import com.katariastoneworld.apis.entity.BillGST;
import com.katariastoneworld.apis.entity.BillKind;
import com.katariastoneworld.apis.entity.BillNonGST;
import com.katariastoneworld.apis.entity.BillPayment;
import com.katariastoneworld.apis.entity.BillPaymentMode;
import com.katariastoneworld.apis.entity.CustomerAdvance;
import com.katariastoneworld.apis.entity.CustomerAdvanceUsage;
import com.katariastoneworld.apis.entity.CustomerWalletTransaction;
import com.katariastoneworld.apis.entity.ClientTransaction;
import com.katariastoneworld.apis.entity.ClientTransactionType;
import com.katariastoneworld.apis.entity.Customer;
import com.katariastoneworld.apis.entity.Expense;
import com.katariastoneworld.apis.entity.MoneyDirection;
import com.katariastoneworld.apis.entity.MoneyCategory;
import com.katariastoneworld.apis.entity.MoneyPaymentMode;
import com.katariastoneworld.apis.entity.MoneyTransaction;
import com.katariastoneworld.apis.constants.BillLifecycleStatus;
import com.katariastoneworld.apis.constants.MoneyLedgerCategories;
import com.katariastoneworld.apis.repository.BillGSTRepository;
import com.katariastoneworld.apis.repository.BillNonGSTRepository;
import com.katariastoneworld.apis.repository.BillPaymentRepository;
import com.katariastoneworld.apis.repository.CustomerAdvanceRepository;
import com.katariastoneworld.apis.repository.CustomerAdvanceUsageRepository;
import com.katariastoneworld.apis.repository.CustomerWalletTransactionRepository;
import com.katariastoneworld.apis.repository.ExpenseRepository;
import com.katariastoneworld.apis.repository.ClientTransactionRepository;
import com.katariastoneworld.apis.repository.MoneyTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class DailyClosingReportService {

    private static final BigDecimal EPS = new BigDecimal("0.01");
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    /** Inclusive period length above this triggers a non-blocking warning in the API response. */
    private static final int LARGE_RANGE_WARNING_INCLUSIVE_DAYS = 90;
    private static final BigDecimal CAUSE_MIN = new BigDecimal("0.01");

    private static final class CreditSlice {
        private BigDecimal remaining;
        private final BillPaymentMode mode;

        private CreditSlice(BigDecimal remaining, BillPaymentMode mode) {
            this.remaining = remaining;
            this.mode = mode;
        }
    }

    private record ModeBuckets(BigDecimal cash, BigDecimal upi, BigDecimal bank, BigDecimal other) {
    }

    @Autowired
    private BillGSTRepository billGSTRepository;

    @Autowired
    private BillNonGSTRepository billNonGSTRepository;

    @Autowired
    private BillPaymentRepository billPaymentRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private CustomerAdvanceRepository customerAdvanceRepository;

    @Autowired
    private CustomerAdvanceUsageRepository customerAdvanceUsageRepository;

    @Autowired
    private CustomerWalletTransactionRepository customerWalletTransactionRepository;

    @Autowired
    private MoneyTransactionRepository moneyTransactionRepository;

    @Autowired
    private ClientTransactionRepository clientTransactionRepository;

    @Autowired
    private BalanceSummaryService balanceSummaryService;

    private static final List<MoneyPaymentMode> IN_HAND_PAYMENT_MODES = List.of(MoneyPaymentMode.CASH, MoneyPaymentMode.UPI);
    private static final List<MoneyPaymentMode> ALL_MONEY_MODES = List.of(
            MoneyPaymentMode.CASH, MoneyPaymentMode.UPI, MoneyPaymentMode.BANK);
    /** Synthetic {@link DailyClosingExpenseLineDTO#getId()} base for client/supplier payment rows from {@code transactions}. */
    private static final long CLIENT_PAYMENT_EXPENSE_LINE_ID_BASE = 8_000_000_000L;

    /**
     * Single calendar day (same as {@link #buildReportForPeriod} with {@code from == to}).
     */
    public DailyClosingReportDTO buildReport(LocalDate date, String location) {
        return buildReportForPeriod(date, date, location);
    }

    /**
     * Bills whose {@code bill_date} is in [{@code from}, {@code to}] (inclusive); collections and expenses
     * use {@code payment_date} / {@code expenses.expense_date} in the same range.
     */
    public DailyClosingReportDTO buildReportForPeriod(LocalDate from, LocalDate to, String location) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("End date must be on or after start date");
        }
        final String loc = location == null ? "" : location.trim();

        List<BillGST> gstBills = billGSTRepository.findByBillLocationAndBillDateBetween(loc, from, to);
        List<BillNonGST> nonBills = billNonGSTRepository.findByBillLocationAndBillDateBetween(loc, from, to);

        Map<Long, List<BillPayment>> gstPaysByBill = loadPaymentsByBillId(BillKind.GST, gstBills.stream().map(BillGST::getId).toList());
        Map<Long, List<BillPayment>> nonPaysByBill = loadPaymentsByBillId(BillKind.NON_GST, nonBills.stream().map(BillNonGST::getId).toList());
        Map<String, BigDecimal> advanceUsedByBill = loadAdvanceUsedByBill(gstBills, nonBills);

        List<DailyClosingBillLineDTO> lines = new ArrayList<>();
        BigDecimal grossSales = BigDecimal.ZERO;
        BigDecimal supplementarySales = BigDecimal.ZERO;
        BigDecimal cancelledSales = BigDecimal.ZERO;
        BigDecimal pendingOnBilledDay = BigDecimal.ZERO;

        for (BillGST b : gstBills) {
            List<BillPayment> pays = gstPaysByBill.getOrDefault(b.getId(), List.of());
            BigDecimal advanceUsed = advanceUsedByBill.getOrDefault(paymentKey(BillKind.GST, b.getId()), ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            lines.add(toLineGst(b, pays, advanceUsed));
            BigDecimal billTotal = b.getTotalAmount() != null ? b.getTotalAmount() : ZERO;
            if (Boolean.TRUE.equals(b.getSupplementaryBill())) {
                supplementarySales = supplementarySales.add(billTotal);
            } else {
                grossSales = grossSales.add(billTotal);
            }
            if (isCancelledGstBill(b)) {
                cancelledSales = cancelledSales.add(billTotal);
            }
            pendingOnBilledDay = pendingOnBilledDay.add(computeDue(b.getTotalAmount(), pays, advanceUsed, b.getPaymentMethod(),
                    b.getPaymentStatus().name()));
        }
        for (BillNonGST b : nonBills) {
            List<BillPayment> pays = nonPaysByBill.getOrDefault(b.getId(), List.of());
            BigDecimal advanceUsed = advanceUsedByBill.getOrDefault(paymentKey(BillKind.NON_GST, b.getId()), ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            lines.add(toLineNon(b, pays, advanceUsed));
            BigDecimal billTotal = b.getTotalAmount() != null ? b.getTotalAmount() : ZERO;
            if (Boolean.TRUE.equals(b.getSupplementaryBill())) {
                supplementarySales = supplementarySales.add(billTotal);
            } else {
                grossSales = grossSales.add(billTotal);
            }
            if (isCancelledNonGstBill(b)) {
                cancelledSales = cancelledSales.add(billTotal);
            }
            pendingOnBilledDay = pendingOnBilledDay.add(computeDue(b.getTotalAmount(), pays, advanceUsed, b.getPaymentMethod(),
                    b.getPaymentStatus().name()));
        }
        grossSales = grossSales.setScale(2, RoundingMode.HALF_UP);
        supplementarySales = supplementarySales.setScale(2, RoundingMode.HALF_UP);
        cancelledSales = cancelledSales.setScale(2, RoundingMode.HALF_UP);

        lines.sort(Comparator
                .comparing(DailyClosingBillLineDTO::getBillDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(DailyClosingBillLineDTO::getBillType)
                .thenComparing(DailyClosingBillLineDTO::getBillId));

        BigDecimal totalPaidOnBills = lines.stream()
                .map(l -> BigDecimal.valueOf(l.getPaidAmount() != null ? l.getPaidAmount() : 0.0))
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        List<Expense> expenseRows = expenseRepository.findByLocationAndDateBetween(loc, from, to);
        List<DailyClosingExpenseLineDTO> expenseLineDtos = expenseRows.stream()
                .sorted(Comparator.comparing(Expense::getDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Expense::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(e -> DailyClosingExpenseLineDTO.builder()
                        .id(e.getId())
                        .expenseType(e.getType())
                        .date(e.getDate())
                        .category(e.getCategory())
                        .amount(e.getAmount() != null ? e.getAmount().doubleValue() : 0.0)
                        .paymentMethod(e.getPaymentMethod())
                        .description(e.getDescription())
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));

        BigDecimal clientSupplierPayOut = moneyTransactionRepository
                .sumOutByLocationDateRangeModesAndCategories(loc, from, to, ALL_MONEY_MODES,
                        List.of(MoneyCategory.CLIENT_PAYMENT))
                .setScale(2, RoundingMode.HALF_UP);
        for (MoneyTransaction m : moneyTransactionRepository.findClientPaymentOutLinesForReport(loc, from, to)) {
            expenseLineDtos.add(DailyClosingExpenseLineDTO.builder()
                    .id(CLIENT_PAYMENT_EXPENSE_LINE_ID_BASE + m.getId())
                    .expenseType("client_payment")
                    .date(m.getTransactionDate())
                    .category("client_supplier")
                    .amount(m.getAmount() != null ? m.getAmount().doubleValue() : 0.0)
                    .paymentMethod(m.getPaymentMode() != null
                            ? m.getPaymentMode().name().toLowerCase(Locale.ROOT)
                            : "cash")
                    .description(m.getNotes())
                    .build());
        }
        expenseLineDtos.sort(Comparator
                .comparing(DailyClosingExpenseLineDTO::getDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(DailyClosingExpenseLineDTO::getId, Comparator.nullsLast(Comparator.naturalOrder())));

        List<BillPayment> collectedInPeriod = from.equals(to)
                ? billPaymentRepository.findByPaymentDateAndBillLocation(loc, from)
                : billPaymentRepository.findByPaymentDateBetweenAndBillLocation(loc, from, to);
        BigDecimal totalCollectedFromBills = collectedInPeriod.stream()
                .filter(p -> !isRefundPaymentRow(p))
                .map(BillPayment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal salesReturns = moneyTransactionRepository
                .sumOutByLocationDateRangeModesAndCategories(loc, from, to, IN_HAND_PAYMENT_MODES,
                        MoneyLedgerCategories.NON_EXPENSE_OUT)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal netSales = grossSales.subtract(salesReturns).add(supplementarySales).max(ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        Map<String, Double> paymentSummary = new LinkedHashMap<>();
        paymentSummary.put("CASH", 0.0);
        paymentSummary.put("UPI", 0.0);
        paymentSummary.put("BANK_TRANSFER", 0.0);
        paymentSummary.put("CHEQUE", 0.0);
        paymentSummary.put("OTHER", 0.0);
        for (BillPayment p : collectedInPeriod) {
            if (isRefundPaymentRow(p) || p.getAmount() == null) {
                continue;
            }
            if (p.getPaymentMode() == null) {
                paymentSummary.merge("OTHER", p.getAmount().doubleValue(), Double::sum);
                continue;
            }
            String k = p.getPaymentMode().name();
            paymentSummary.merge(k, p.getAmount().doubleValue(), Double::sum);
        }

        BigDecimal inHandCollectedFromBills = collectedInPeriod.stream()
                .filter(p -> !isRefundPaymentRow(p))
                .filter(p -> p.getPaymentMode() == BillPaymentMode.CASH || p.getPaymentMode() == BillPaymentMode.UPI)
                .map(BillPayment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal expenseTableTotal = expenseRows.stream()
                .map(Expense::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal expenses = expenseTableTotal.add(clientSupplierPayOut).setScale(2, RoundingMode.HALF_UP);

        LocalDateTime periodStart = from.atStartOfDay();
        LocalDateTime periodEndExclusive = to.plusDays(1).atStartOfDay();
        List<CustomerWalletTransaction> advanceDepositsInPeriod = customerWalletTransactionRepository
                .findByLocationAndTxnTypeAndSourceAndCreatedAtRange(
                        loc,
                        CustomerWalletTransaction.TxnType.CREDIT,
                        CustomerWalletTransaction.Status.ACTIVE,
                        "ADVANCE_DEPOSIT",
                        periodStart,
                        periodEndExclusive);
        BigDecimal advanceDeposits = customerWalletTransactionRepository
                .sumByLocationAndTxnTypeAndSourceAndCreatedAtRange(
                        loc,
                        CustomerWalletTransaction.TxnType.CREDIT,
                        CustomerWalletTransaction.Status.ACTIVE,
                        "ADVANCE_DEPOSIT",
                        periodStart,
                        periodEndExclusive)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal advanceApplied = customerWalletTransactionRepository
                .sumByLocationAndTxnTypeAndSourceAndCreatedAtRange(
                        loc,
                        CustomerWalletTransaction.TxnType.DEBIT,
                        CustomerWalletTransaction.Status.ACTIVE,
                        "BILL_PAYMENT",
                        periodStart,
                        periodEndExclusive)
                .setScale(2, RoundingMode.HALF_UP);
        // "Advance Available" should reflect current active wallet balance for the location.
        // This includes bill-cancel refund credits, even if refund happened outside selected bill date window.
        BigDecimal advanceAvailable = customerWalletTransactionRepository
                .getActiveWalletBalanceByLocation(
                        loc,
                        CustomerWalletTransaction.Status.ACTIVE,
                        CustomerWalletTransaction.TxnType.CREDIT)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal advanceInHand = ZERO;
        for (CustomerWalletTransaction adv : advanceDepositsInPeriod) {
            if (adv.getAmount() == null) {
                continue;
            }
            BigDecimal amount = adv.getAmount().setScale(2, RoundingMode.HALF_UP);
            BillPaymentMode mode = adv.getPaymentMode() != null ? adv.getPaymentMode() : BillPaymentMode.CASH;
            paymentSummary.merge(mode.name(), amount.doubleValue(), Double::sum);
            if (mode == BillPaymentMode.CASH || mode == BillPaymentMode.UPI) {
                advanceInHand = advanceInHand.add(amount);
            }
        }

        BigDecimal totalCollected = totalCollectedFromBills.add(advanceDeposits).setScale(2, RoundingMode.HALF_UP);
        BigDecimal inHandCollected = inHandCollectedFromBills.add(advanceInHand).setScale(2, RoundingMode.HALF_UP);

        List<ClientTransaction> clientInflowRows = clientTransactionRepository
                .findByLocationAndTransactionTypeAndTransactionDateBetweenOrderByTransactionDateDescIdDesc(
                        loc, ClientTransactionType.PAYMENT_IN, from, to);
        BigDecimal clientPaymentsIn = clientInflowRows.stream()
                .map(ClientTransaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal clientInHand = clientInflowRows.stream()
                .filter(tx -> tx.getPaymentMode() == BillPaymentMode.CASH || tx.getPaymentMode() == BillPaymentMode.UPI)
                .map(ClientTransaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        for (ClientTransaction tx : clientInflowRows) {
            BillPaymentMode mode = tx.getPaymentMode() != null ? tx.getPaymentMode() : BillPaymentMode.OTHER;
            BigDecimal amount = tx.getAmount() != null ? tx.getAmount().setScale(2, RoundingMode.HALF_UP) : ZERO;
            paymentSummary.merge(mode.name(), amount.doubleValue(), Double::sum);
        }

        totalCollected = totalCollected.add(clientPaymentsIn).setScale(2, RoundingMode.HALF_UP);
        inHandCollected = inHandCollected.add(clientInHand).setScale(2, RoundingMode.HALF_UP);

        Map<String, Double> loanReceiptsByMode = new LinkedHashMap<>();
        loanReceiptsByMode.put("CASH", 0.0);
        loanReceiptsByMode.put("UPI", 0.0);
        loanReceiptsByMode.put("BANK_TRANSFER", 0.0);
        loanReceiptsByMode.put("CHEQUE", 0.0);
        loanReceiptsByMode.put("OTHER", 0.0);
        BigDecimal loanCash = moneyTransactionRepository
                .sumInByLocationDateRangeCategoryModes(loc, from, to, MoneyCategory.LOAN, List.of(MoneyPaymentMode.CASH))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal loanUpi = moneyTransactionRepository
                .sumInByLocationDateRangeCategoryModes(loc, from, to, MoneyCategory.LOAN, List.of(MoneyPaymentMode.UPI))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal loanBank = moneyTransactionRepository
                .sumInByLocationDateRangeCategoryModes(loc, from, to, MoneyCategory.LOAN, List.of(MoneyPaymentMode.BANK))
                .setScale(2, RoundingMode.HALF_UP);
        loanReceiptsByMode.put("CASH", loanCash.doubleValue());
        loanReceiptsByMode.put("UPI", loanUpi.doubleValue());
        loanReceiptsByMode.put("BANK_TRANSFER", loanBank.doubleValue());
        BigDecimal totalLoanReceipts = loanCash.add(loanUpi).add(loanBank).setScale(2, RoundingMode.HALF_UP);

        BigDecimal cashInHand = inHandCollected.subtract(expenses).setScale(2, RoundingMode.HALF_UP);
        DailyCashFlowDTO cashFlow = buildCashFlowView(from, loc, totalCollectedFromBills, advanceDeposits, expenses);

        BigDecimal dueOnBills = pendingOnBilledDay.setScale(2, RoundingMode.HALF_UP);

        BigDecimal summarySum = paymentSummary.values().stream()
                .map(BigDecimal::valueOf)
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal reconDelta = summarySum.subtract(totalCollected).abs().setScale(2, RoundingMode.HALF_UP);
        boolean reconOk = reconDelta.compareTo(new BigDecimal("0.02")) <= 0;

        List<String> warnings = new ArrayList<>();
        long inclusiveDays = from.until(to, ChronoUnit.DAYS) + 1;
        if (inclusiveDays > LARGE_RANGE_WARNING_INCLUSIVE_DAYS) {
            warnings.add("Date range is longer than " + LARGE_RANGE_WARNING_INCLUSIVE_DAYS
                    + " days; the report may take longer to load.");
        }
        if (!reconOk) {
            warnings.add("Collections reconciliation: sum of payment modes differs from total collected by "
                    + reconDelta.toPlainString() + " (check rounding or data).");
        }

        return DailyClosingReportDTO.builder()
                .date(from)
                .dateTo(to)
                .location(loc)
                .totalBills(gstBills.size() + nonBills.size())
                .totalSales(netSales.doubleValue())
                .grossSales(grossSales.doubleValue())
                .supplementarySales(supplementarySales.doubleValue())
                .cancelledSales(cancelledSales.doubleValue())
                .netSales(netSales.doubleValue())
                .salesReturns(salesReturns.doubleValue())
                .billRefundsCashUpi(salesReturns.doubleValue())
                .totalPaidOnBills(totalPaidOnBills.doubleValue())
                .totalDueOnBills(dueOnBills.doubleValue())
                .totalCollected(totalCollected.doubleValue())
                .paymentSummary(paymentSummary)
                .loanReceiptsByMode(loanReceiptsByMode)
                .totalLoanReceipts(totalLoanReceipts.doubleValue())
                .warnings(warnings)
                .collectionsReconciliationOk(reconOk)
                .collectionsReconciliationDelta(reconDelta.doubleValue())
                .totalExpenses(expenses.doubleValue())
                .totalAdvanceDeposits(advanceDeposits.doubleValue())
                .totalAdvanceAppliedOnBills(advanceApplied.doubleValue())
                .totalAdvanceAvailable(advanceAvailable.doubleValue())
                .pendingAmount(dueOnBills.doubleValue())
                .inHandAmount(cashInHand.doubleValue())
                .cashInHand(cashInHand.doubleValue())
                .bills(lines)
                .expenseLines(expenseLineDtos)
                .cashFlow(cashFlow)
                .build();
    }

    private DailyCashFlowDTO buildCashFlowView(LocalDate fromDate,
            String location,
            BigDecimal salesCollection,
            BigDecimal advanceReceived,
            BigDecimal expenses) {
        BigDecimal opening = balanceSummaryService.openingCashUpiAtStartOfDay(location, fromDate);
        BigDecimal sales = nvl(salesCollection);
        BigDecimal advance = nvl(advanceReceived);
        BigDecimal expense = nvl(expenses);
        BigDecimal closing = opening.add(sales).add(advance).subtract(expense).setScale(2, RoundingMode.HALF_UP);
        return DailyCashFlowDTO.builder()
                .opening(scale2(opening))
                .salesCollection(scale2(sales))
                .advanceReceived(scale2(advance))
                .expenses(scale2(expense))
                .closingBalance(scale2(closing))
                .build();
    }

    /**
     * Admin repair operation: backfills missing payment rows for legacy PAID bills in a single day.
     */
    public int repairLegacyBillPaymentsForDate(LocalDate date, String location) {
        LocalDate d = date != null ? date : LocalDate.now();
        String loc = location == null ? "" : location.trim();
        int inserted = 0;
        List<BillGST> gstBills = billGSTRepository.findByBillLocationAndBillDateBetween(loc, d, d);
        List<BillNonGST> nonBills = billNonGSTRepository.findByBillLocationAndBillDateBetween(loc, d, d);
        for (BillGST bill : gstBills) {
            if (backfillLegacyPaymentIfNeededGst(bill)) {
                inserted++;
            }
        }
        for (BillNonGST bill : nonBills) {
            if (backfillLegacyPaymentIfNeededNon(bill)) {
                inserted++;
            }
        }
        return inserted;
    }

    private Map<Long, List<BillPayment>> loadPaymentsByBillId(BillKind kind, List<Long> billIds) {
        if (billIds == null || billIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<BillPayment> all = billPaymentRepository.findByBillKindAndBillIdIn(kind, billIds);
        Map<Long, List<BillPayment>> map = all.stream().collect(Collectors.groupingBy(BillPayment::getBillId));
        for (List<BillPayment> list : map.values()) {
            list.sort(Comparator.comparing(BillPayment::getId));
        }
        return map;
    }

    private boolean backfillLegacyPaymentIfNeededGst(BillGST bill) {
        List<BillPayment> existing = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.GST, bill.getId());
        if (!existing.isEmpty()) {
            return false;
        }
        if (bill.getPaymentStatus() != BillGST.PaymentStatus.PAID) {
            return false;
        }
        String pm = bill.getPaymentMethod();
        if (pm == null || pm.isBlank() || "-".equals(pm.trim())) {
            return false;
        }
        BillPaymentMode mode;
        try {
            mode = BillPaymentMode.parseFlexible(pm);
        } catch (IllegalArgumentException ex) {
            mode = BillPaymentMode.BANK_TRANSFER;
        }
        BillPayment row = new BillPayment();
        row.setBillKind(BillKind.GST);
        row.setBillId(bill.getId());
        row.setAmount(bill.getTotalAmount().setScale(2, RoundingMode.HALF_UP));
        row.setPaymentMode(mode);
        row.setPaymentDate(bill.getBillDate() != null ? bill.getBillDate() : LocalDate.now());
        billPaymentRepository.save(row);
        return true;
    }

    private boolean backfillLegacyPaymentIfNeededNon(BillNonGST bill) {
        List<BillPayment> existing = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.NON_GST, bill.getId());
        if (!existing.isEmpty()) {
            return false;
        }
        if (bill.getPaymentStatus() != BillNonGST.PaymentStatus.PAID) {
            return false;
        }
        String pm = bill.getPaymentMethod();
        if (pm == null || pm.isBlank() || "-".equals(pm.trim())) {
            return false;
        }
        BillPaymentMode mode;
        try {
            mode = BillPaymentMode.parseFlexible(pm);
        } catch (IllegalArgumentException ex) {
            mode = BillPaymentMode.BANK_TRANSFER;
        }
        BillPayment row = new BillPayment();
        row.setBillKind(BillKind.NON_GST);
        row.setBillId(bill.getId());
        row.setAmount(bill.getTotalAmount().setScale(2, RoundingMode.HALF_UP));
        row.setPaymentMode(mode);
        row.setPaymentDate(bill.getBillDate() != null ? bill.getBillDate() : LocalDate.now());
        billPaymentRepository.save(row);
        return true;
    }

    private static String paymentKey(BillKind kind, Long billId) {
        return kind.name() + ":" + billId;
    }

    private Map<String, BigDecimal> loadAdvanceUsedByBill(List<BillGST> gstBills, List<BillNonGST> nonBills) {
        Map<String, BigDecimal> map = new HashMap<>();

        List<Long> gstIds = gstBills.stream().map(BillGST::getId).filter(Objects::nonNull).toList();
        if (!gstIds.isEmpty()) {
            for (CustomerAdvanceUsage u : customerAdvanceUsageRepository.findByBillKindAndBillIdIn(BillKind.GST, gstIds)) {
                if (u.getBillId() == null || u.getAmountUsed() == null) continue;
                map.merge(
                        paymentKey(BillKind.GST, u.getBillId()),
                        u.getAmountUsed().setScale(2, RoundingMode.HALF_UP),
                        BigDecimal::add);
            }
        }

        List<Long> nonIds = nonBills.stream().map(BillNonGST::getId).filter(Objects::nonNull).toList();
        if (!nonIds.isEmpty()) {
            for (CustomerAdvanceUsage u : customerAdvanceUsageRepository.findByBillKindAndBillIdIn(BillKind.NON_GST, nonIds)) {
                if (u.getBillId() == null || u.getAmountUsed() == null) continue;
                map.merge(
                        paymentKey(BillKind.NON_GST, u.getBillId()),
                        u.getAmountUsed().setScale(2, RoundingMode.HALF_UP),
                        BigDecimal::add);
            }
        }
        return map;
    }

    private static DailyClosingBillLineDTO toLineGst(BillGST b, List<BillPayment> pays, BigDecimal advanceUsed) {
        BigDecimal total = b.getTotalAmount().setScale(2, RoundingMode.HALF_UP);
        String statusName = b.getPaymentStatus().name();
        String pm = b.getPaymentMethod();
        BigDecimal paid = computePaid(total, pays, advanceUsed, pm, statusName);
        BigDecimal due = total.subtract(paid).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal overpaidAmt = paid.subtract(total).max(ZERO);
        if (overpaidAmt.compareTo(EPS) <= 0) {
            overpaidAmt = ZERO;
        }
        ModeBuckets buckets = computeModeBuckets(pays, paid, total, pm, statusName);
        String lineStatus = isCancelledGstBill(b) ? "CANCELLED" : deriveLineStatus(total, paid);
        return DailyClosingBillLineDTO.builder()
                .billType("GST")
                .billId(b.getId())
                .billNumber(b.getBillNumber())
                .billDate(b.getBillDate())
                .totalAmount(total.doubleValue())
                .paidAmount(paid.doubleValue())
                .dueAmount(due.doubleValue())
                .status(lineStatus)
                .paymentModes(formatModes(pays, pm))
                .cashAmount(scale2(buckets.cash()))
                .upiAmount(scale2(buckets.upi()))
                .bankTransferAmount(scale2(buckets.bank()))
                .otherAmount(scale2(buckets.other()))
                .overpaidAmount(scale2(overpaidAmt))
                .build();
    }

    private static DailyClosingBillLineDTO toLineNon(BillNonGST b, List<BillPayment> pays, BigDecimal advanceUsed) {
        BigDecimal total = b.getTotalAmount().setScale(2, RoundingMode.HALF_UP);
        String statusName = b.getPaymentStatus().name();
        String pm = b.getPaymentMethod();
        BigDecimal paid = computePaid(total, pays, advanceUsed, pm, statusName);
        BigDecimal due = total.subtract(paid).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal overpaidAmt = paid.subtract(total).max(ZERO);
        if (overpaidAmt.compareTo(EPS) <= 0) {
            overpaidAmt = ZERO;
        }
        ModeBuckets buckets = computeModeBuckets(pays, paid, total, pm, statusName);
        String lineStatus = isCancelledNonGstBill(b) ? "CANCELLED" : deriveLineStatus(total, paid);
        return DailyClosingBillLineDTO.builder()
                .billType("NON_GST")
                .billId(b.getId())
                .billNumber(b.getBillNumber())
                .billDate(b.getBillDate())
                .totalAmount(total.doubleValue())
                .paidAmount(paid.doubleValue())
                .dueAmount(due.doubleValue())
                .status(lineStatus)
                .paymentModes(formatModes(pays, pm))
                .cashAmount(scale2(buckets.cash()))
                .upiAmount(scale2(buckets.upi()))
                .bankTransferAmount(scale2(buckets.bank()))
                .otherAmount(scale2(buckets.other()))
                .overpaidAmount(scale2(overpaidAmt))
                .build();
    }

    private static double scale2(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Per-bill split: from payment rows, or one bucket for legacy PAID full with no rows.
     */
    private static ModeBuckets computeModeBuckets(List<BillPayment> pays, BigDecimal paid, BigDecimal billTotal,
            String storedMethod, String paymentStatusName) {
        if (pays != null && !pays.isEmpty()) {
            return sumModes(pays);
        }
        boolean inferLegacyFull = storedMethod != null
                && !storedMethod.isBlank()
                && !"-".equals(storedMethod.trim())
                && "PAID".equals(paymentStatusName)
                && paid.compareTo(billTotal) >= 0;
        if (inferLegacyFull) {
            try {
                BillPaymentMode m = BillPaymentMode.parseFlexible(storedMethod);
                return singleBucket(paid, m);
            } catch (IllegalArgumentException ex) {
                return new ModeBuckets(ZERO, ZERO, ZERO, paid);
            }
        }
        return new ModeBuckets(ZERO, ZERO, ZERO, ZERO);
    }

    private static ModeBuckets sumModes(List<BillPayment> pays) {
        BigDecimal c = ZERO;
        BigDecimal u = ZERO;
        BigDecimal b = ZERO;
        BigDecimal o = ZERO;
        for (BillPayment p : pays) {
            if (p.getAmount() == null) {
                continue;
            }
            BigDecimal a = p.getAmount().setScale(2, RoundingMode.HALF_UP);
            if (p.getPaymentMode() == null) {
                o = o.add(a);
                continue;
            }
            switch (p.getPaymentMode()) {
                case CASH -> c = c.add(a);
                case UPI -> u = u.add(a);
                case BANK_TRANSFER -> b = b.add(a);
                case CHEQUE -> o = o.add(a);
                default -> o = o.add(a);
            }
        }
        return new ModeBuckets(c, u, b, o);
    }

    private static ModeBuckets singleBucket(BigDecimal amount, BillPaymentMode m) {
        BigDecimal a = amount.setScale(2, RoundingMode.HALF_UP);
        return switch (m) {
            case CASH -> new ModeBuckets(a, ZERO, ZERO, ZERO);
            case UPI -> new ModeBuckets(ZERO, a, ZERO, ZERO);
            case BANK_TRANSFER -> new ModeBuckets(ZERO, ZERO, a, ZERO);
            case CHEQUE -> new ModeBuckets(ZERO, ZERO, ZERO, a);
            case WALLET -> new ModeBuckets(ZERO, ZERO, ZERO, a);
            case OTHER -> new ModeBuckets(ZERO, ZERO, ZERO, a);
        };
    }

    private static BigDecimal computePaid(BigDecimal billTotal, List<BillPayment> pays, BigDecimal advanceUsed, String storedMethod,
            String paymentStatusName) {
        BigDecimal paid = pays.stream()
                .map(BillPayment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal adv = advanceUsed == null ? ZERO : advanceUsed.setScale(2, RoundingMode.HALF_UP);
        paid = paid.add(adv).setScale(2, RoundingMode.HALF_UP);
        boolean inferLegacyFull = pays.isEmpty()
                && storedMethod != null
                && !storedMethod.isBlank()
                && !"-".equals(storedMethod.trim())
                && "PAID".equals(paymentStatusName)
                && paid.compareTo(BigDecimal.ZERO) == 0;
        if (inferLegacyFull) {
            paid = billTotal;
        }
        return paid;
    }

    private static BigDecimal computeDue(BigDecimal billTotal, List<BillPayment> pays, BigDecimal advanceUsed, String storedMethod,
            String paymentStatusName) {
        BigDecimal paid = computePaid(billTotal, pays, advanceUsed, storedMethod, paymentStatusName);
        return billTotal.subtract(paid).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private static String deriveLineStatus(BigDecimal total, BigDecimal paid) {
        if (paid.compareTo(BigDecimal.ZERO) <= 0) {
            return "DUE";
        }
        if (paid.compareTo(total) >= 0 || paid.subtract(total).abs().compareTo(EPS) <= 0) {
            return "PAID";
        }
        return "PARTIAL";
    }

    private static String formatModes(List<BillPayment> pays, String legacyMethod) {
        if (pays != null && !pays.isEmpty()) {
            return pays.stream()
                    .map(p -> p.getPaymentMode() == null ? "OTHER" : p.getPaymentMode().name())
                    .distinct()
                    .collect(Collectors.joining(", "));
        }
        if (legacyMethod != null && !legacyMethod.isBlank() && !"-".equals(legacyMethod.trim())) {
            return legacyMethod.trim();
        }
        return "-";
    }

    public PaymentModeTotalsDTO paymentModeTotalsForSales(LocalDate from, LocalDate to, String location) {
        final String loc = location == null ? "" : location.trim();
        List<BillPayment> rows = from.equals(to)
                ? billPaymentRepository.findByPaymentDateAndBillLocation(loc, from)
                : billPaymentRepository.findByPaymentDateBetweenAndBillLocation(loc, from, to);
        List<BillGST> gstBills = billGSTRepository.findByBillLocationAndBillDateBetween(loc, from, to);
        List<BillNonGST> nonBills = billNonGSTRepository.findByBillLocationAndBillDateBetween(loc, from, to);

        BigDecimal upi = ZERO, cash = ZERO, bank = ZERO, cheque = ZERO, other = ZERO;
        for (BillPayment p : rows) {
            if (p.getAmount() == null) continue;
            BigDecimal a = p.getAmount().setScale(2, RoundingMode.HALF_UP);
            if (p.getPaymentMode() == null) {
                other = other.add(a);
                continue;
            }
            switch (p.getPaymentMode()) {
                case UPI -> upi = upi.add(a);
                case CASH -> cash = cash.add(a);
                case BANK_TRANSFER -> bank = bank.add(a);
                case CHEQUE -> cheque = cheque.add(a);
                case WALLET -> {
                    // Wallet/advance applied is allocated separately using customer wallet debits by original deposit mode.
                }
                default -> other = other.add(a);
            }
        }

        // Advance used on bills should be counted in the original deposit mode bucket, not "other".
        List<String> refs = new ArrayList<>(gstBills.size() + nonBills.size());
        for (BillGST b : gstBills) refs.add("GST:" + b.getId());
        for (BillNonGST b : nonBills) refs.add("NON_GST:" + b.getId());
        if (!refs.isEmpty()) {
            List<CustomerWalletTransaction> advanceDebits = customerWalletTransactionRepository
                    .findBySourceAndTxnTypeAndStatusAndReferenceIdIn(
                            "BILL_PAYMENT",
                            CustomerWalletTransaction.TxnType.DEBIT,
                            CustomerWalletTransaction.Status.ACTIVE,
                            refs);
            Set<String> targetRefs = new HashSet<>(refs);
            Set<Long> targetCustomerIds = advanceDebits.stream()
                    .map(CustomerWalletTransaction::getCustomer)
                    .filter(Objects::nonNull)
                    .map(Customer::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<Long, List<CreditSlice>> fifoByCustomer = new HashMap<>();
            if (!targetCustomerIds.isEmpty()) {
                List<CustomerWalletTransaction> timeline = customerWalletTransactionRepository
                        .findByCustomer_IdInAndStatusOrderByCreatedAtAscIdAsc(
                                new ArrayList<>(targetCustomerIds),
                                CustomerWalletTransaction.Status.ACTIVE);
                for (CustomerWalletTransaction t : timeline) {
                    Long cid = t.getCustomer() != null ? t.getCustomer().getId() : null;
                    if (cid == null) continue;
                    if (t.getTxnType() == CustomerWalletTransaction.TxnType.CREDIT) {
                        BigDecimal amt = nvl(t.getAmount());
                        if (amt.compareTo(ZERO) > 0) {
                            fifoByCustomer.computeIfAbsent(cid, k -> new ArrayList<>())
                                    .add(new CreditSlice(amt, t.getPaymentMode()));
                        }
                        continue;
                    }
                    if (t.getTxnType() != CustomerWalletTransaction.TxnType.DEBIT) continue;
                    BigDecimal debitAmt = nvl(t.getAmount());
                    if (debitAmt.compareTo(ZERO) <= 0) continue;
                    List<CreditSlice> queue = fifoByCustomer.computeIfAbsent(cid, k -> new ArrayList<>());
                    if ("BILL_PAYMENT".equalsIgnoreCase(String.valueOf(t.getSource()))
                            && targetRefs.contains(String.valueOf(t.getReferenceId()))
                            && t.getPaymentMode() == null) {
                        BigDecimal left = debitAmt;
                        for (CreditSlice s : queue) {
                            if (left.compareTo(ZERO) <= 0) break;
                            if (s.remaining.compareTo(ZERO) <= 0) continue;
                            BigDecimal take = s.remaining.min(left).setScale(2, RoundingMode.HALF_UP);
                            if (take.compareTo(ZERO) <= 0) continue;
                            s.remaining = s.remaining.subtract(take).setScale(2, RoundingMode.HALF_UP);
                            left = left.subtract(take).setScale(2, RoundingMode.HALF_UP);
                            BillPaymentMode mode = s.mode;
                            if (mode == BillPaymentMode.UPI) upi = upi.add(take);
                            else if (mode == BillPaymentMode.CASH) cash = cash.add(take);
                            else if (mode == BillPaymentMode.BANK_TRANSFER) bank = bank.add(take);
                            else if (mode == BillPaymentMode.CHEQUE) cheque = cheque.add(take);
                            else other = other.add(take);
                        }
                        if (left.compareTo(ZERO) > 0) {
                            other = other.add(left);
                        }
                    } else {
                        // Consume FIFO for any debit so subsequent bill debits infer correctly.
                        BigDecimal left = debitAmt;
                        for (CreditSlice s : queue) {
                            if (left.compareTo(ZERO) <= 0) break;
                            if (s.remaining.compareTo(ZERO) <= 0) continue;
                            BigDecimal take = s.remaining.min(left).setScale(2, RoundingMode.HALF_UP);
                            if (take.compareTo(ZERO) <= 0) continue;
                            s.remaining = s.remaining.subtract(take).setScale(2, RoundingMode.HALF_UP);
                            left = left.subtract(take).setScale(2, RoundingMode.HALF_UP);
                        }
                    }
                }
            }

            for (CustomerWalletTransaction t : advanceDebits) {
                BigDecimal a = nvl(t.getAmount());
                if (a.compareTo(ZERO) <= 0) continue;
                BillPaymentMode mode = t.getPaymentMode();
                if (mode == null) {
                    // Already bucketed above via FIFO fallback.
                    continue;
                }
                switch (mode) {
                    case UPI -> upi = upi.add(a);
                    case CASH -> cash = cash.add(a);
                    case BANK_TRANSFER -> bank = bank.add(a);
                    case CHEQUE -> cheque = cheque.add(a);
                    default -> other = other.add(a);
                }
            }
        }

        // Backend-only legacy fallback for old bills without payment rows.
        for (BillGST b : gstBills) {
            if (rows.stream().anyMatch(p -> p.getBillKind() == BillKind.GST && Objects.equals(p.getBillId(), b.getId()))) continue;
            if (b.getPaymentStatus() != BillGST.PaymentStatus.PAID) continue;
            BigDecimal total = b.getPaidAmount() != null ? b.getPaidAmount() : b.getTotalAmount();
            if (total == null || total.compareTo(ZERO) <= 0) continue;
            BillPaymentMode mode = parseLegacyMode(b.getPaymentMethod());
            if (mode == null) {
                other = other.add(total);
            } else if (mode == BillPaymentMode.UPI) {
                upi = upi.add(total);
            } else if (mode == BillPaymentMode.CASH) {
                cash = cash.add(total);
            } else if (mode == BillPaymentMode.BANK_TRANSFER) {
                bank = bank.add(total);
            } else if (mode == BillPaymentMode.CHEQUE) {
                cheque = cheque.add(total);
            }
        }
        for (BillNonGST b : nonBills) {
            if (rows.stream().anyMatch(p -> p.getBillKind() == BillKind.NON_GST && Objects.equals(p.getBillId(), b.getId()))) continue;
            if (b.getPaymentStatus() != BillNonGST.PaymentStatus.PAID) continue;
            BigDecimal total = b.getPaidAmount() != null ? b.getPaidAmount() : b.getTotalAmount();
            if (total == null || total.compareTo(ZERO) <= 0) continue;
            BillPaymentMode mode = parseLegacyMode(b.getPaymentMethod());
            if (mode == null) {
                other = other.add(total);
            } else if (mode == BillPaymentMode.UPI) {
                upi = upi.add(total);
            } else if (mode == BillPaymentMode.CASH) {
                cash = cash.add(total);
            } else if (mode == BillPaymentMode.BANK_TRANSFER) {
                bank = bank.add(total);
            } else if (mode == BillPaymentMode.CHEQUE) {
                cheque = cheque.add(total);
            }
        }

        return PaymentModeTotalsDTO.builder()
                .upi(upi.setScale(2, RoundingMode.HALF_UP).doubleValue())
                .cash(cash.setScale(2, RoundingMode.HALF_UP).doubleValue())
                .bankTransfer(bank.setScale(2, RoundingMode.HALF_UP).doubleValue())
                .cheque(cheque.setScale(2, RoundingMode.HALF_UP).doubleValue())
                .other(other.setScale(2, RoundingMode.HALF_UP).doubleValue())
                .build();
    }

    public SalesChargesSummaryDTO salesChargesSummary(LocalDate from, LocalDate to, String location) {
        final String loc = location == null ? "" : location.trim();
        List<BillGST> gstBills = billGSTRepository.findByBillLocationAndBillDateBetween(loc, from, to);
        List<BillNonGST> nonBills = billNonGSTRepository.findByBillLocationAndBillDateBetween(loc, from, to);

        BigDecimal sqft = ZERO;
        BigDecimal labour = ZERO;
        BigDecimal other = ZERO;

        for (BillGST b : gstBills) {
            sqft = sqft.add(nvl(b.getTotalSqft()));
            labour = labour.add(nvl(b.getLabourCharge()));
            other = other.add(nvl(b.getOtherExpenses()));
        }
        for (BillNonGST b : nonBills) {
            sqft = sqft.add(nvl(b.getTotalSqft()));
            labour = labour.add(nvl(b.getLabourCharge()));
            other = other.add(nvl(b.getOtherExpenses()));
        }

        return SalesChargesSummaryDTO.builder()
                .totalSqftSold(sqft.setScale(2, RoundingMode.HALF_UP).doubleValue())
                .totalLabourCharge(labour.setScale(2, RoundingMode.HALF_UP).doubleValue())
                .totalOtherExpensesCharge(other.setScale(2, RoundingMode.HALF_UP).doubleValue())
                .build();
    }

    public ReconciliationReportDTO reconciliation(LocalDate date, String location) {
        String loc = location == null ? "" : location.trim();
        BigDecimal cashUpiIn = nvl(moneyTransactionRepository.sumAmountByLocationDateRangeDirectionModes(
                loc, date, date, MoneyDirection.IN, IN_HAND_PAYMENT_MODES));
        BigDecimal cashUpiOut = nvl(moneyTransactionRepository.sumAmountByLocationDateRangeDirectionModes(
                loc, date, date, MoneyDirection.OUT, IN_HAND_PAYMENT_MODES));
        BigDecimal ledgerTotal = cashUpiIn.subtract(cashUpiOut).setScale(2, RoundingMode.HALF_UP);
        BigDecimal budgetNetMovement = ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal delta = ledgerTotal.subtract(budgetNetMovement).setScale(2, RoundingMode.HALF_UP);
        boolean ok = true;
        List<ReconciliationCauseDTO> causes = buildReconciliationCauses(date, loc);
        return ReconciliationReportDTO.builder()
                .location(loc)
                .date(String.valueOf(date))
                .ledgerTotal(ledgerTotal.doubleValue())
                .budgetNetMovement(budgetNetMovement.doubleValue())
                .delta(delta.doubleValue())
                .level(ok ? "OK" : "WARNING")
                .message("CASH/UPI net for the day from transactions.")
                .possibleCauses(causes)
                .build();
    }

    private List<ReconciliationCauseDTO> buildReconciliationCauses(LocalDate date, String location) {
        List<ReconciliationCauseDTO> causes = new ArrayList<>();
        List<BillGST> gstBills = billGSTRepository.findByBillLocationAndBillDateBetween(location, date, date);
        List<BillNonGST> nonBills = billNonGSTRepository.findByBillLocationAndBillDateBetween(location, date, date);

        BigDecimal missingPaymentImpact = ZERO;
        int missingPaymentBills = 0;
        for (BillGST bill : gstBills) {
            if (isMissingPaymentEntry(bill.getPaymentStatus(), bill.getPaymentMethod(),
                    billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.GST, bill.getId()))) {
                missingPaymentBills++;
                missingPaymentImpact = missingPaymentImpact.add(nvl(bill.getTotalAmount()));
            }
        }
        for (BillNonGST bill : nonBills) {
            if (isMissingPaymentEntry(bill.getPaymentStatus(), bill.getPaymentMethod(),
                    billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.NON_GST, bill.getId()))) {
                missingPaymentBills++;
                missingPaymentImpact = missingPaymentImpact.add(nvl(bill.getTotalAmount()));
            }
        }
        if (missingPaymentImpact.compareTo(CAUSE_MIN) >= 0) {
            causes.add(ReconciliationCauseDTO.builder()
                    .causeCode("MISSING_PAYMENT_ENTRY")
                    .title("Possible missing payment entries")
                    .details("Found " + missingPaymentBills + " paid/partial bills without payment rows on this date.")
                    .estimatedImpact(scale2(missingPaymentImpact))
                    .autoResolvable(true)
                    .resolveAction("BACKFILL_MISSING_PAYMENT_ENTRIES")
                    .build());
        }

        List<BillPayment> payments = billPaymentRepository.findByPaymentDateAndBillLocation(location, date);
        BigDecimal duplicatePaymentImpact = estimateDuplicatePaymentImpact(payments);
        if (duplicatePaymentImpact.compareTo(CAUSE_MIN) >= 0) {
            causes.add(ReconciliationCauseDTO.builder()
                    .causeCode("DUPLICATE_PAYMENT_ENTRY")
                    .title("Possible duplicate payment rows")
                    .details("Detected exact duplicate bill payment entries (same bill, mode, amount, date).")
                    .estimatedImpact(scale2(duplicatePaymentImpact))
                    .autoResolvable(false)
                    .resolveAction(null)
                    .build());
        }

        List<Expense> expenses = expenseRepository.findByLocationAndDate(location, date);
        BigDecimal duplicateExpenseImpact = estimateDuplicateExpenseImpact(expenses);
        if (duplicateExpenseImpact.compareTo(CAUSE_MIN) >= 0) {
            causes.add(ReconciliationCauseDTO.builder()
                    .causeCode("POSSIBLE_EXTRA_EXPENSE")
                    .title("Possible extra/duplicate expense entries")
                    .details("Detected similar expense rows with same amount/category/description/date.")
                    .estimatedImpact(scale2(duplicateExpenseImpact))
                    .autoResolvable(false)
                    .resolveAction(null)
                    .build());
        }
        return causes;
    }

    private static boolean isMissingPaymentEntry(Enum<?> paymentStatus, String paymentMethod, List<BillPayment> paymentRows) {
        boolean looksPaid = paymentStatus != null
                && ("PAID".equals(paymentStatus.name()) || "PARTIAL".equals(paymentStatus.name())
                        || "REFUND_PENDING".equals(paymentStatus.name()));
        boolean hasLegacyMethod = paymentMethod != null && !paymentMethod.isBlank() && !"-".equals(paymentMethod.trim());
        return looksPaid && hasLegacyMethod && (paymentRows == null || paymentRows.isEmpty());
    }

    private static BigDecimal estimateDuplicatePaymentImpact(List<BillPayment> payments) {
        Map<String, List<BillPayment>> grouped = payments.stream().collect(Collectors.groupingBy(p ->
                p.getBillKind() + "|" + p.getBillId() + "|" + p.getPaymentDate() + "|" + p.getPaymentMode() + "|" + nvl(p.getAmount())));
        BigDecimal duplicateImpact = ZERO;
        for (List<BillPayment> rows : grouped.values()) {
            if (rows.size() <= 1) {
                continue;
            }
            BigDecimal amount = nvl(rows.get(0).getAmount());
            duplicateImpact = duplicateImpact.add(amount.multiply(BigDecimal.valueOf(rows.size() - 1L)));
        }
        return duplicateImpact.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal estimateDuplicateExpenseImpact(List<Expense> expenses) {
        Map<String, List<Expense>> grouped = expenses.stream().collect(Collectors.groupingBy(e ->
                String.valueOf(e.getDate()) + "|" + safe(e.getCategory()) + "|" + safe(e.getDescription()) + "|" + nvl(e.getAmount())));
        BigDecimal duplicateImpact = ZERO;
        for (List<Expense> rows : grouped.values()) {
            if (rows.size() <= 1) {
                continue;
            }
            BigDecimal amount = nvl(rows.get(0).getAmount());
            duplicateImpact = duplicateImpact.add(amount.multiply(BigDecimal.valueOf(rows.size() - 1L)));
        }
        return duplicateImpact.setScale(2, RoundingMode.HALF_UP);
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static BigDecimal nvl(BigDecimal amount) {
        return amount == null ? ZERO : amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static BillPaymentMode parseLegacyMode(String raw) {
        if (raw == null || raw.isBlank() || "-".equals(raw.trim())) {
            return null;
        }
        String t = raw.trim().toUpperCase(Locale.ROOT);
        if (t.startsWith("C")) return BillPaymentMode.CASH;
        if (t.startsWith("U")) return BillPaymentMode.UPI;
        if (t.startsWith("B")) return BillPaymentMode.BANK_TRANSFER;
        if (t.startsWith("Q")) return BillPaymentMode.CHEQUE;
        try {
            return BillPaymentMode.parseFlexible(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean isCancelledGstBill(BillGST b) {
        if (b == null) {
            return false;
        }
        if (Boolean.TRUE.equals(b.getIsDeleted())) {
            return true;
        }
        if (b.getPaymentStatus() == BillGST.PaymentStatus.CANCELLED) {
            return true;
        }
        String st = b.getBillStatus();
        return st != null && BillLifecycleStatus.CANCELLED.equalsIgnoreCase(st.trim());
    }

    private static boolean isCancelledNonGstBill(BillNonGST b) {
        if (b == null) {
            return false;
        }
        if (Boolean.TRUE.equals(b.getIsDeleted())) {
            return true;
        }
        if (b.getPaymentStatus() == BillNonGST.PaymentStatus.CANCELLED) {
            return true;
        }
        String st = b.getBillStatus();
        return st != null && BillLifecycleStatus.CANCELLED.equalsIgnoreCase(st.trim());
    }

    /** Negative / reversal payment rows are refunds, not collections. */
    private static boolean isRefundPaymentRow(BillPayment p) {
        if (p == null) {
            return true;
        }
        if (p.getAmount() != null && p.getAmount().compareTo(ZERO) < 0) {
            return true;
        }
        if (p.getPaymentStatus() != null && "REVERSAL".equalsIgnoreCase(p.getPaymentStatus().trim())) {
            return true;
        }
        String src = p.getSourceType();
        return src != null && src.toUpperCase(Locale.ROOT).contains("REVERSAL");
    }
}
