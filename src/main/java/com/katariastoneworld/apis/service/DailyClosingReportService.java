package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.DailyClosingBillLineDTO;
import com.katariastoneworld.apis.dto.DailyClosingExpenseLineDTO;
import com.katariastoneworld.apis.dto.DailyClosingReportDTO;
import com.katariastoneworld.apis.dto.PaymentModeTotalsDTO;
import com.katariastoneworld.apis.dto.ReconciliationReportDTO;
import com.katariastoneworld.apis.entity.*;
import com.katariastoneworld.apis.repository.BillGSTRepository;
import com.katariastoneworld.apis.repository.BillNonGSTRepository;
import com.katariastoneworld.apis.repository.BillPaymentRepository;
import com.katariastoneworld.apis.repository.CustomerAdvanceRepository;
import com.katariastoneworld.apis.repository.CustomerAdvanceUsageRepository;
import com.katariastoneworld.apis.repository.ExpenseRepository;
import com.katariastoneworld.apis.repository.FinancialLedgerRepository;
import com.katariastoneworld.apis.repository.DailyBudgetRepository;
import com.katariastoneworld.apis.repository.ClientTransactionRepository;
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
    private FinancialLedgerRepository financialLedgerRepository;

    @Autowired
    private DailyBudgetRepository dailyBudgetRepository;

    @Autowired
    private ClientTransactionRepository clientTransactionRepository;

    /**
     * Single calendar day (same as {@link #buildReportForPeriod} with {@code from == to}).
     *
     * @param backfillLegacy when true, persists a single {@code bill_payments} row for PAID legacy bills
     *                       (has {@code payment_method}, no rows) among that day's bills only.
     */
    public DailyClosingReportDTO buildReport(LocalDate date, String location, boolean backfillLegacy) {
        return buildReportForPeriod(date, date, location, backfillLegacy);
    }

    /**
     * Bills whose {@code bill_date} is in [{@code from}, {@code to}] (inclusive); collections and expenses
     * use {@code payment_date} / {@code expenses.date} in the same range. Legacy backfill runs only when
     * {@code from.equals(to)} and {@code backfillLegacy} is true.
     */
    public DailyClosingReportDTO buildReportForPeriod(LocalDate from, LocalDate to, String location, boolean backfillLegacy) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("End date must be on or after start date");
        }
        final String loc = location == null ? "" : location.trim();

        List<BillGST> gstBills = billGSTRepository.findByBillLocationAndBillDateBetween(loc, from, to);
        List<BillNonGST> nonBills = billNonGSTRepository.findByBillLocationAndBillDateBetween(loc, from, to);

        if (backfillLegacy && from.equals(to)) {
            gstBills.forEach(this::backfillLegacyPaymentIfNeededGst);
            nonBills.forEach(this::backfillLegacyPaymentIfNeededNon);
        }

        Map<Long, List<BillPayment>> gstPaysByBill = loadPaymentsByBillId(BillKind.GST, gstBills.stream().map(BillGST::getId).toList());
        Map<Long, List<BillPayment>> nonPaysByBill = loadPaymentsByBillId(BillKind.NON_GST, nonBills.stream().map(BillNonGST::getId).toList());
        Map<String, BigDecimal> advanceUsedByBill = loadAdvanceUsedByBill(gstBills, nonBills);

        List<DailyClosingBillLineDTO> lines = new ArrayList<>();
        BigDecimal totalSales = BigDecimal.ZERO;
        BigDecimal pendingOnBilledDay = BigDecimal.ZERO;

        for (BillGST b : gstBills) {
            List<BillPayment> pays = gstPaysByBill.getOrDefault(b.getId(), List.of());
            BigDecimal advanceUsed = advanceUsedByBill.getOrDefault(paymentKey(BillKind.GST, b.getId()), ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            lines.add(toLineGst(b, pays, advanceUsed));
            totalSales = totalSales.add(b.getTotalAmount());
            pendingOnBilledDay = pendingOnBilledDay.add(computeDue(b.getTotalAmount(), pays, advanceUsed, b.getPaymentMethod(),
                    b.getPaymentStatus().name()));
        }
        for (BillNonGST b : nonBills) {
            List<BillPayment> pays = nonPaysByBill.getOrDefault(b.getId(), List.of());
            BigDecimal advanceUsed = advanceUsedByBill.getOrDefault(paymentKey(BillKind.NON_GST, b.getId()), ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            lines.add(toLineNon(b, pays, advanceUsed));
            totalSales = totalSales.add(b.getTotalAmount());
            pendingOnBilledDay = pendingOnBilledDay.add(computeDue(b.getTotalAmount(), pays, advanceUsed, b.getPaymentMethod(),
                    b.getPaymentStatus().name()));
        }

        lines.sort(Comparator
                .comparing(DailyClosingBillLineDTO::getBillDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(DailyClosingBillLineDTO::getBillType)
                .thenComparing(DailyClosingBillLineDTO::getBillId));

        BigDecimal totalPaidOnBills = lines.stream()
                .map(l -> BigDecimal.valueOf(l.getPaidAmount() != null ? l.getPaidAmount() : 0.0))
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        List<DailyClosingExpenseLineDTO> expenseLineDtos = expenseRepository.findByLocationAndDateBetween(loc, from, to).stream()
                .sorted(Comparator.comparing(Expense::getDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Expense::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(e -> DailyClosingExpenseLineDTO.builder()
                        .id(e.getId())
                        .expenseType(e.getType())
                        .category(e.getCategory())
                        .amount(e.getAmount() != null ? e.getAmount().doubleValue() : 0.0)
                        .description(e.getDescription())
                        .build())
                .collect(Collectors.toList());

        List<BillPayment> collectedInPeriod = from.equals(to)
                ? billPaymentRepository.findByPaymentDateAndBillLocation(loc, from)
                : billPaymentRepository.findByPaymentDateBetweenAndBillLocation(loc, from, to);
        BigDecimal totalCollectedFromBills = collectedInPeriod.stream()
                .map(BillPayment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        Map<String, Double> paymentSummary = new LinkedHashMap<>();
        paymentSummary.put("CASH", 0.0);
        paymentSummary.put("UPI", 0.0);
        paymentSummary.put("BANK_TRANSFER", 0.0);
        paymentSummary.put("CHEQUE", 0.0);
        paymentSummary.put("OTHER", 0.0);
        for (BillPayment p : collectedInPeriod) {
            if (p.getAmount() == null) {
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
                .filter(p -> p.getPaymentMode() == BillPaymentMode.CASH || p.getPaymentMode() == BillPaymentMode.UPI)
                .map(BillPayment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal expenses = expenseRepository.findByLocationAndDateBetween(loc, from, to).stream()
                .map(Expense::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        LocalDateTime periodStart = from.atStartOfDay();
        LocalDateTime periodEndExclusive = to.plusDays(1).atStartOfDay();
        List<CustomerAdvance> advanceDepositsInPeriod = customerAdvanceRepository
                .findDepositsForLocationAndCreatedAtRange(loc, periodStart, periodEndExclusive);
        BigDecimal advanceDeposits = customerAdvanceRepository
                .sumDepositsForLocationAndCreatedAtRange(loc, periodStart, periodEndExclusive)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal advanceApplied = customerAdvanceUsageRepository
                .sumUsageForLocationAndCreatedAtRange(loc, periodStart, periodEndExclusive)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal advanceAvailable = advanceDeposits.subtract(advanceApplied).setScale(2, RoundingMode.HALF_UP);

        BigDecimal advanceInHand = ZERO;
        for (CustomerAdvance adv : advanceDepositsInPeriod) {
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
        BigDecimal cashInHand = inHandCollected.subtract(expenses).setScale(2, RoundingMode.HALF_UP);

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
                .totalSales(totalSales.setScale(2, RoundingMode.HALF_UP).doubleValue())
                .totalPaidOnBills(totalPaidOnBills.doubleValue())
                .totalDueOnBills(dueOnBills.doubleValue())
                .totalCollected(totalCollected.doubleValue())
                .paymentSummary(paymentSummary)
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
                .build();
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

    private void backfillLegacyPaymentIfNeededGst(BillGST bill) {
        List<BillPayment> existing = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.GST, bill.getId());
        if (!existing.isEmpty()) {
            return;
        }
        if (bill.getPaymentStatus() != BillGST.PaymentStatus.PAID) {
            return;
        }
        String pm = bill.getPaymentMethod();
        if (pm == null || pm.isBlank() || "-".equals(pm.trim())) {
            return;
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
    }

    private void backfillLegacyPaymentIfNeededNon(BillNonGST bill) {
        List<BillPayment> existing = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.NON_GST, bill.getId());
        if (!existing.isEmpty()) {
            return;
        }
        if (bill.getPaymentStatus() != BillNonGST.PaymentStatus.PAID) {
            return;
        }
        String pm = bill.getPaymentMethod();
        if (pm == null || pm.isBlank() || "-".equals(pm.trim())) {
            return;
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
        return DailyClosingBillLineDTO.builder()
                .billType("GST")
                .billId(b.getId())
                .billNumber(b.getBillNumber())
                .billDate(b.getBillDate())
                .totalAmount(total.doubleValue())
                .paidAmount(paid.doubleValue())
                .dueAmount(due.doubleValue())
                .status(deriveLineStatus(total, paid))
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
        return DailyClosingBillLineDTO.builder()
                .billType("NON_GST")
                .billId(b.getId())
                .billNumber(b.getBillNumber())
                .billDate(b.getBillDate())
                .totalAmount(total.doubleValue())
                .paidAmount(paid.doubleValue())
                .dueAmount(due.doubleValue())
                .status(deriveLineStatus(total, paid))
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
                default -> other = other.add(a);
            }
        }

        // Backend-only legacy fallback for old bills without payment rows.
        List<BillGST> gstBills = billGSTRepository.findByBillLocationAndBillDateBetween(loc, from, to);
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
        List<BillNonGST> nonBills = billNonGSTRepository.findByBillLocationAndBillDateBetween(loc, from, to);
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

    public ReconciliationReportDTO reconciliation(LocalDate date, String location) {
        String loc = location == null ? "" : location.trim();
        BigDecimal ledgerTotal = financialLedgerRepository
                .sumInHandByLocationAndDateRange(loc, date, date)
                .setScale(2, RoundingMode.HALF_UP);

        DailyBudget budget = dailyBudgetRepository.findFirstByLocationAndUserIdIsNull(loc)
                .or(() -> dailyBudgetRepository.findByLocation(loc))
                .orElse(null);
        BigDecimal budgetNetMovement = ZERO.setScale(2, RoundingMode.HALF_UP);
        if (budget != null && budget.getAmount() != null && budget.getRemainingBudget() != null) {
            // Movement produced by collection adjustments beyond planned budget-spend baseline.
            budgetNetMovement = budget.getRemainingBudget().subtract(budget.getAmount()).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal delta = ledgerTotal.subtract(budgetNetMovement).setScale(2, RoundingMode.HALF_UP);
        boolean ok = delta.abs().compareTo(new BigDecimal("0.02")) <= 0;
        return new ReconciliationReportDTO(
                loc,
                String.valueOf(date),
                ledgerTotal.doubleValue(),
                budgetNetMovement.doubleValue(),
                delta.doubleValue(),
                ok ? "OK" : "WARNING",
                ok ? "Ledger and budget are reconciled." : "Mismatch between ledger and budget movements.");
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
}
