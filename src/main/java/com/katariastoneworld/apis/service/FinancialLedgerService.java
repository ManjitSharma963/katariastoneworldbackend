package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.LedgerRequest;
import com.katariastoneworld.apis.dto.LedgerSummaryDTO;
import com.katariastoneworld.apis.dto.PaymentModeLedgerAnalyticsDTO;
import com.katariastoneworld.apis.dto.PaymentModeLedgerRowDTO;
import com.katariastoneworld.apis.dto.ProfitLossReportDTO;
import com.katariastoneworld.apis.dto.ReferenceTypeExpenseReportDTO;
import com.katariastoneworld.apis.dto.ReferenceTypeExpenseRowDTO;
import com.katariastoneworld.apis.entity.BillKind;
import com.katariastoneworld.apis.entity.BillPaymentMode;
import com.katariastoneworld.apis.entity.FinancialLedgerEntry;
import com.katariastoneworld.apis.entity.LedgerEntryType;
import com.katariastoneworld.apis.repository.FinancialLedgerRepository;
import com.katariastoneworld.apis.util.LedgerAuditContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;

/**
 * Central ledger writer: idempotent for an active row per ({@code source_type}, {@code source_id});
 * DB unique is ({@code source_type}, {@code source_id}, {@code is_deleted}).
 * No daily-budget side effects — balances come from ledger queries.
 */
@Service
@Transactional
public class FinancialLedgerService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final FinancialLedgerRepository financialLedgerRepository;

    public FinancialLedgerService(FinancialLedgerRepository financialLedgerRepository) {
        this.financialLedgerRepository = financialLedgerRepository;
    }

    /**
     * Insert or reactivate a row for ({@code source_type}, {@code source_id}). Idempotent when an active row exists.
     * Throws if required fields are missing or amount is not positive.
     */
    public void createEntry(LedgerRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("Ledger request is required");
        }
        if (req.getLocation() == null || req.getLocation().isBlank()) {
            throw new IllegalArgumentException("Ledger location is required");
        }
        if (req.getSourceType() == null || req.getSourceType().isBlank()) {
            throw new IllegalArgumentException("Ledger sourceType is required");
        }
        if (req.getSourceId() == null || req.getSourceId().isBlank()) {
            throw new IllegalArgumentException("Ledger sourceId is required");
        }
        if (req.getEntryType() == null) {
            throw new IllegalArgumentException("Ledger entryType is required");
        }
        if (req.getAmount() == null) {
            throw new IllegalArgumentException("Ledger amount is required");
        }
        BigDecimal amt = req.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Ledger amount must be positive");
        }
        String st = req.getSourceType().trim();
        String sid = req.getSourceId().trim();
        if (financialLedgerRepository.findBySourceTypeAndSourceIdAndIsDeletedFalse(st, sid).isPresent()) {
            return;
        }
        List<FinancialLedgerEntry> any = financialLedgerRepository.findBySourceTypeAndSourceId(st, sid);
        if (!any.isEmpty()) {
            FinancialLedgerEntry row = any.stream()
                    .max(Comparator.comparing(FinancialLedgerEntry::getId, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElseThrow();
            if (Boolean.TRUE.equals(row.getIsDeleted())) {
                applyRequestToRow(row, req, amt);
                row.setIsDeleted(false);
                stampMutation(row);
                financialLedgerRepository.save(row);
            }
            return;
        }
        FinancialLedgerEntry row = new FinancialLedgerEntry();
        applyRequestToRow(row, req, amt);
        row.setIsDeleted(false);
        stampCreatedNew(row);
        financialLedgerRepository.save(row);
    }

    private void applyRequestToRow(FinancialLedgerEntry row, LedgerRequest req, BigDecimal amt) {
        BillPaymentMode mode = req.getPaymentMode() != null ? req.getPaymentMode() : BillPaymentMode.OTHER;
        String st = req.getSourceType().trim();
        row.setSourceType(st);
        row.setSourceId(req.getSourceId().trim());
        row.setEntryType(req.getEntryType());
        row.setEventType(mapEventType(st));
        row.setLocation(req.getLocation().trim());
        row.setAmount(amt);
        row.setPaymentMode(mode);
        row.setReferenceType(trimToNull(req.getReferenceType()));
        row.setReferenceId(trimToNull(req.getReferenceId()));
        row.setEventDate(req.getEventDate() != null ? req.getEventDate() : LocalDate.now());
        row.setBillKind(req.getBillKind() != null ? req.getBillKind().name() : null);
        row.setBillId(req.getBillId());
        row.setCustomerId(req.getCustomerId());
        row.setInHandAmount(ZERO);
    }

    private void stampCreatedNew(FinancialLedgerEntry row) {
        Long u = LedgerAuditContext.getUserIdOrNull();
        if (u != null) {
            row.setCreatedBy(u);
        }
    }

    private void stampMutation(FinancialLedgerEntry row) {
        row.setUpdatedAt(LocalDateTime.now());
        Long u = LedgerAuditContext.getUserIdOrNull();
        if (u != null) {
            row.setUpdatedBy(u);
        }
    }

    /** Ledger totals for a location and inclusive date range (active rows only). */
    public LedgerSummaryDTO summarize(String location, LocalDate from, LocalDate to) {
        final String loc = location == null ? "" : location.trim();
        LocalDate f = from != null ? from : LocalDate.now();
        LocalDate t = to != null ? to : f;
        if (t.isBefore(f)) {
            LocalDate tmp = f;
            f = t;
            t = tmp;
        }
        BigDecimal credits = financialLedgerRepository
                .sumAmountByLocationDateRangeAndEntryType(loc, f, t, LedgerEntryType.CREDIT)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal debits = financialLedgerRepository
                .sumAmountByLocationDateRangeAndEntryType(loc, f, t, LedgerEntryType.DEBIT)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal expenseDebits = financialLedgerRepository
                .sumDebitByLocationDateRangeAndSourceType(loc, f, t, "EXPENSE")
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = credits.subtract(debits).setScale(2, RoundingMode.HALF_UP);
        return LedgerSummaryDTO.builder()
                .location(loc)
                .date(f)
                .dateTo(t)
                .totalCredit(credits.doubleValue())
                .totalDebit(debits.doubleValue())
                .netBalance(net.doubleValue())
                .expenseDebitTotal(expenseDebits.doubleValue())
                .build();
    }

    /** Same totals as {@link #summarize}; explicit profit-and-loss naming for reporting APIs. */
    public ProfitLossReportDTO profitAndLoss(String location, LocalDate from, LocalDate to) {
        LedgerSummaryDTO s = summarize(location, from, to);
        return ProfitLossReportDTO.builder()
                .location(s.getLocation())
                .date(s.getDate())
                .dateTo(s.getDateTo())
                .revenue(s.getTotalCredit())
                .expense(s.getTotalDebit())
                .net(s.getNetBalance())
                .build();
    }

    /** DEBIT totals grouped by {@code reference_type} (ledger truth). */
    public ReferenceTypeExpenseReportDTO expensesByReferenceType(String location, LocalDate from, LocalDate to) {
        final String loc = location == null ? "" : location.trim();
        LocalDate f = from != null ? from : LocalDate.now();
        LocalDate t = to != null ? to : f;
        if (t.isBefore(f)) {
            LocalDate tmp = f;
            f = t;
            t = tmp;
        }
        List<Object[]> raw = financialLedgerRepository.sumDebitGroupedByReferenceType(loc, f, t);
        List<ReferenceTypeExpenseRowDTO> rows = new ArrayList<>();
        for (Object[] row : raw) {
            if (row == null || row.length < 2) {
                continue;
            }
            String ref = row[0] != null ? (String) row[0] : "(none)";
            BigDecimal sum = row[1] instanceof BigDecimal b ? b : BigDecimal.ZERO;
            sum = sum.setScale(2, RoundingMode.HALF_UP);
            rows.add(ReferenceTypeExpenseRowDTO.builder()
                    .referenceType(ref)
                    .totalAmount(sum.doubleValue())
                    .build());
        }
        rows.sort(Comparator.comparing(ReferenceTypeExpenseRowDTO::getReferenceType,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return ReferenceTypeExpenseReportDTO.builder()
                .location(loc)
                .date(f)
                .dateTo(t)
                .byReferenceType(rows)
                .build();
    }

    /** CREDIT vs DEBIT per {@code payment_mode} (full ledger, not bill table). */
    public PaymentModeLedgerAnalyticsDTO paymentModeLedgerAnalytics(String location, LocalDate from, LocalDate to) {
        final String loc = location == null ? "" : location.trim();
        LocalDate f = from != null ? from : LocalDate.now();
        LocalDate t = to != null ? to : f;
        if (t.isBefore(f)) {
            LocalDate tmp = f;
            f = t;
            t = tmp;
        }
        EnumMap<BillPaymentMode, BigDecimal> credits = new EnumMap<>(BillPaymentMode.class);
        EnumMap<BillPaymentMode, BigDecimal> debits = new EnumMap<>(BillPaymentMode.class);
        for (Object[] row : financialLedgerRepository.sumAmountGroupedByPaymentModeAndEntryType(loc, f, t)) {
            if (row == null || row.length < 3 || row[0] == null || row[1] == null) {
                continue;
            }
            BillPaymentMode mode = (BillPaymentMode) row[0];
            LedgerEntryType et = (LedgerEntryType) row[1];
            BigDecimal sum = row[2] instanceof BigDecimal b ? b : BigDecimal.ZERO;
            sum = sum.setScale(2, RoundingMode.HALF_UP);
            if (et == LedgerEntryType.CREDIT) {
                credits.merge(mode, sum, BigDecimal::add);
            } else if (et == LedgerEntryType.DEBIT) {
                debits.merge(mode, sum, BigDecimal::add);
            }
        }
        List<PaymentModeLedgerRowDTO> out = new ArrayList<>();
        for (BillPaymentMode m : BillPaymentMode.values()) {
            BigDecimal c = credits.getOrDefault(m, ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal d = debits.getOrDefault(m, ZERO).setScale(2, RoundingMode.HALF_UP);
            if (c.compareTo(ZERO) == 0 && d.compareTo(ZERO) == 0) {
                continue;
            }
            out.add(PaymentModeLedgerRowDTO.builder()
                    .paymentMode(m.name())
                    .creditTotal(c.doubleValue())
                    .debitTotal(d.doubleValue())
                    .net(c.subtract(d).setScale(2, RoundingMode.HALF_UP).doubleValue())
                    .build());
        }
        return PaymentModeLedgerAnalyticsDTO.builder()
                .location(loc)
                .date(f)
                .dateTo(t)
                .byPaymentMode(out)
                .build();
    }

    public void softDeleteBySourceTypeAndSourceId(String sourceType, String sourceId) {
        if (sourceType == null || sourceId == null) {
            return;
        }
        financialLedgerRepository
                .findBySourceTypeAndSourceIdAndIsDeletedFalse(sourceType.trim(), sourceId.trim())
                .ifPresent(row -> {
                    row.setIsDeleted(true);
                    stampMutation(row);
                    financialLedgerRepository.save(row);
                });
    }

    /**
     * Upsert active ledger row for a manual expense (after {@link com.katariastoneworld.apis.entity.Expense} is saved).
     */
    public void upsertExpenseLedger(com.katariastoneworld.apis.entity.Expense expense) {
        if (expense == null || expense.getId() == null || Boolean.TRUE.equals(expense.getIsDeleted())) {
            return;
        }
        String sid = String.valueOf(expense.getId());
        BillPaymentMode mode = parseExpensePaymentMethod(expense.getPaymentMethod());
        BigDecimal amt = expense.getAmount() != null ? expense.getAmount().setScale(2, RoundingMode.HALF_UP) : ZERO;
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        LocalDate d = expense.getDate() != null ? expense.getDate() : LocalDate.now();
        String loc = expense.getLocation() != null ? expense.getLocation().trim() : "";
        if (loc.isEmpty()) {
            return;
        }
        var opt = financialLedgerRepository.findBySourceTypeAndSourceIdAndIsDeletedFalse("EXPENSE", sid);
        if (opt.isEmpty()) {
            createEntry(LedgerRequest.builder()
                    .location(loc)
                    .sourceType("EXPENSE")
                    .sourceId(sid)
                    .entryType(LedgerEntryType.DEBIT)
                    .amount(amt)
                    .paymentMode(mode)
                    .referenceType("EXPENSE")
                    .referenceId(sid)
                    .eventDate(d)
                    .build());
            return;
        }
        FinancialLedgerEntry row = opt.get();
        row.setAmount(amt);
        row.setPaymentMode(mode);
        row.setEventDate(d);
        row.setLocation(loc);
        row.setEntryType(LedgerEntryType.DEBIT);
        row.setReferenceType("EXPENSE");
        row.setReferenceId(sid);
        row.setInHandAmount(ZERO);
        stampMutation(row);
        financialLedgerRepository.save(row);
    }

    public void syncBillPayment(String location, BillKind billKind, Long billId, Long paymentId,
            BillPaymentMode mode, BigDecimal amount, LocalDate paymentDate, boolean active) {
        if (location == null || location.isBlank() || billKind == null || billId == null || paymentId == null) {
            return;
        }
        String sourceType = "BILL_PAYMENT";
        String sourceId = String.valueOf(paymentId);
        var opt = financialLedgerRepository.findBySourceTypeAndSourceIdAndIsDeletedFalse(sourceType, sourceId);
        if (!active) {
            opt.ifPresent(row -> {
                row.setIsDeleted(true);
                stampMutation(row);
                financialLedgerRepository.save(row);
            });
            return;
        }
        if (mode == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        LocalDate ed = paymentDate != null ? paymentDate : LocalDate.now();
        if (opt.isEmpty()) {
            createEntry(LedgerRequest.builder()
                    .location(location.trim())
                    .sourceType(sourceType)
                    .sourceId(sourceId)
                    .entryType(LedgerEntryType.CREDIT)
                    .amount(amt)
                    .paymentMode(mode)
                    .referenceType("BILL")
                    .referenceId(String.valueOf(billId))
                    .eventDate(ed)
                    .billKind(billKind)
                    .billId(billId)
                    .build());
            return;
        }
        FinancialLedgerEntry row = opt.get();
        row.setAmount(amt);
        row.setPaymentMode(mode);
        row.setEventDate(ed);
        row.setLocation(location.trim());
        row.setBillKind(billKind.name());
        row.setBillId(billId);
        row.setReferenceType("BILL");
        row.setReferenceId(String.valueOf(billId));
        row.setEntryType(LedgerEntryType.CREDIT);
        row.setEventType(FinancialLedgerEntry.EventType.BILL_PAYMENT);
        row.setInHandAmount(ZERO);
        stampMutation(row);
        financialLedgerRepository.save(row);
    }

    public void recordBillPayment(String location, BillKind billKind, Long billId, Long paymentId,
            BillPaymentMode mode, BigDecimal amount, LocalDate paymentDate) {
        if (paymentId == null) {
            return;
        }
        syncBillPayment(location, billKind, billId, paymentId, mode, amount, paymentDate, true);
    }

    public void recordAdvanceDeposit(String location, Long customerId, Long advanceId, BillPaymentMode mode, BigDecimal amount,
            LocalDate eventDate) {
        if (location == null || location.isBlank() || advanceId == null || customerId == null || mode == null
                || amount == null) {
            return;
        }
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        createEntry(LedgerRequest.builder()
                .location(location.trim())
                .sourceType("ADVANCE")
                .sourceId(String.valueOf(advanceId))
                .entryType(LedgerEntryType.CREDIT)
                .amount(amt)
                .paymentMode(mode)
                .referenceType("CUSTOMER")
                .referenceId(String.valueOf(customerId))
                .eventDate(eventDate != null ? eventDate : LocalDate.now())
                .customerId(customerId)
                .build());
    }

    public void recordClientPaymentIn(String location, String clientId, Long clientTransactionId, BillPaymentMode mode,
            BigDecimal amount, LocalDate eventDate) {
        if (location == null || location.isBlank() || clientTransactionId == null || mode == null || amount == null
                || clientId == null || clientId.isBlank()) {
            return;
        }
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        createEntry(LedgerRequest.builder()
                .location(location.trim())
                .sourceType("CLIENT_PAYMENT_IN")
                .sourceId(String.valueOf(clientTransactionId))
                .entryType(LedgerEntryType.CREDIT)
                .amount(amt)
                .paymentMode(mode)
                .referenceType("CUSTOMER")
                .referenceId(clientId.trim())
                .eventDate(eventDate != null ? eventDate : LocalDate.now())
                .build());
    }

    private static FinancialLedgerEntry.EventType mapEventType(String sourceType) {
        if (sourceType == null) {
            return FinancialLedgerEntry.EventType.EXPENSE;
        }
        return switch (sourceType) {
            case "BILL_PAYMENT" -> FinancialLedgerEntry.EventType.BILL_PAYMENT;
            case "EXPENSE" -> FinancialLedgerEntry.EventType.EXPENSE;
            case "ADVANCE" -> FinancialLedgerEntry.EventType.CUSTOMER_ADVANCE;
            case "ADVANCE_DEPOSIT" -> FinancialLedgerEntry.EventType.ADVANCE_DEPOSIT;
            case "SALARY" -> FinancialLedgerEntry.EventType.SALARY;
            case "EMPLOYEE_ADVANCE" -> FinancialLedgerEntry.EventType.EMPLOYEE_ADVANCE;
            case "CLIENT_PAYMENT_IN" -> FinancialLedgerEntry.EventType.CLIENT_PAYMENT_IN;
            case "CLIENT_TRANSACTION" -> FinancialLedgerEntry.EventType.CLIENT_DEBIT;
            default -> FinancialLedgerEntry.EventType.EXPENSE;
        };
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static BillPaymentMode parseExpensePaymentMethod(String raw) {
        if (raw == null || raw.isBlank()) {
            return BillPaymentMode.OTHER;
        }
        try {
            return BillPaymentMode.parseFlexible(raw.trim());
        } catch (IllegalArgumentException ex) {
            return BillPaymentMode.OTHER;
        }
    }
}
