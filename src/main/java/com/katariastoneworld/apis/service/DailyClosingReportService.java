package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.DailyClosingBillLineDTO;
import com.katariastoneworld.apis.dto.DailyClosingExpenseLineDTO;
import com.katariastoneworld.apis.dto.DailyClosingReportDTO;
import com.katariastoneworld.apis.entity.*;
import com.katariastoneworld.apis.repository.BillGSTRepository;
import com.katariastoneworld.apis.repository.BillNonGSTRepository;
import com.katariastoneworld.apis.repository.BillPaymentRepository;
import com.katariastoneworld.apis.repository.ExpenseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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

        List<BillGST> gstBills = billGSTRepository.findByCustomerLocationAndBillDateBetween(loc, from, to);
        List<BillNonGST> nonBills = billNonGSTRepository.findByCustomerLocationAndBillDateBetween(loc, from, to);

        if (backfillLegacy && from.equals(to)) {
            gstBills.forEach(this::backfillLegacyPaymentIfNeededGst);
            nonBills.forEach(this::backfillLegacyPaymentIfNeededNon);
        }

        Map<Long, List<BillPayment>> gstPaysByBill = loadPaymentsByBillId(BillKind.GST, gstBills.stream().map(BillGST::getId).toList());
        Map<Long, List<BillPayment>> nonPaysByBill = loadPaymentsByBillId(BillKind.NON_GST, nonBills.stream().map(BillNonGST::getId).toList());

        List<DailyClosingBillLineDTO> lines = new ArrayList<>();
        BigDecimal totalSales = BigDecimal.ZERO;
        BigDecimal pendingOnBilledDay = BigDecimal.ZERO;

        for (BillGST b : gstBills) {
            List<BillPayment> pays = gstPaysByBill.getOrDefault(b.getId(), List.of());
            lines.add(toLineGst(b, pays));
            totalSales = totalSales.add(b.getTotalAmount());
            pendingOnBilledDay = pendingOnBilledDay.add(computeDue(b.getTotalAmount(), pays, b.getPaymentMethod(),
                    b.getPaymentStatus().name()));
        }
        for (BillNonGST b : nonBills) {
            List<BillPayment> pays = nonPaysByBill.getOrDefault(b.getId(), List.of());
            lines.add(toLineNon(b, pays));
            totalSales = totalSales.add(b.getTotalAmount());
            pendingOnBilledDay = pendingOnBilledDay.add(computeDue(b.getTotalAmount(), pays, b.getPaymentMethod(),
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
        BigDecimal totalCollected = collectedInPeriod.stream()
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

        BigDecimal cashCollected = collectedInPeriod.stream()
                .filter(p -> p.getPaymentMode() == BillPaymentMode.CASH)
                .map(BillPayment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal expenses = expenseRepository.findByLocationAndDateBetween(loc, from, to).stream()
                .map(Expense::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal cashInHand = cashCollected.subtract(expenses).setScale(2, RoundingMode.HALF_UP);

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
                .pendingAmount(dueOnBills.doubleValue())
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

    private static DailyClosingBillLineDTO toLineGst(BillGST b, List<BillPayment> pays) {
        BigDecimal total = b.getTotalAmount().setScale(2, RoundingMode.HALF_UP);
        String statusName = b.getPaymentStatus().name();
        String pm = b.getPaymentMethod();
        BigDecimal paid = computePaid(total, pays, pm, statusName);
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

    private static DailyClosingBillLineDTO toLineNon(BillNonGST b, List<BillPayment> pays) {
        BigDecimal total = b.getTotalAmount().setScale(2, RoundingMode.HALF_UP);
        String statusName = b.getPaymentStatus().name();
        String pm = b.getPaymentMethod();
        BigDecimal paid = computePaid(total, pays, pm, statusName);
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
        };
    }

    private static BigDecimal computePaid(BigDecimal billTotal, List<BillPayment> pays, String storedMethod,
            String paymentStatusName) {
        BigDecimal paid = pays.stream()
                .map(BillPayment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
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

    private static BigDecimal computeDue(BigDecimal billTotal, List<BillPayment> pays, String storedMethod,
            String paymentStatusName) {
        BigDecimal paid = computePaid(billTotal, pays, storedMethod, paymentStatusName);
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
}
