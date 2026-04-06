package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.*;
import com.katariastoneworld.apis.entity.*;
import com.katariastoneworld.apis.event.BillPaymentLedgerSyncEvent;
import com.katariastoneworld.apis.repository.BudgetManualAdjustmentRepository;
import com.katariastoneworld.apis.repository.DailyBudgetRepository;
import com.katariastoneworld.apis.repository.LocationDayBudgetRepository;
import com.katariastoneworld.apis.repository.MoneyTransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class UnifiedCashbookService {

    public static final String REF_EXPENSE = "EXPENSE";
    public static final String REF_BILL_PAYMENT = "BILL_PAYMENT";
    public static final String REF_MANUAL = "MANUAL_ENTRY";

    private final MoneyTransactionRepository moneyTransactionRepository;
    private final LocationDayBudgetRepository locationDayBudgetRepository;
    private final BudgetManualAdjustmentRepository budgetManualAdjustmentRepository;
    private final DailyBudgetRepository dailyBudgetRepository;

    public UnifiedCashbookService(MoneyTransactionRepository moneyTransactionRepository,
            LocationDayBudgetRepository locationDayBudgetRepository,
            BudgetManualAdjustmentRepository budgetManualAdjustmentRepository,
            DailyBudgetRepository dailyBudgetRepository) {
        this.moneyTransactionRepository = moneyTransactionRepository;
        this.locationDayBudgetRepository = locationDayBudgetRepository;
        this.budgetManualAdjustmentRepository = budgetManualAdjustmentRepository;
        this.dailyBudgetRepository = dailyBudgetRepository;
    }

    public MoneyTransactionResponseDTO createTransaction(MoneyTransactionRequestDTO req, String location, Long userId) {
        final String loc = normalizeLoc(location);
        LocalDate d = req.getEventDate() != null ? req.getEventDate() : LocalDate.now();
        BigDecimal amt = req.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        getOrCreateDayLocked(loc, d);

        MoneyTransaction t = new MoneyTransaction();
        t.setTxType(req.getType());
        t.setAmount(amt);
        t.setCategory(req.getCategory() != null ? req.getCategory().trim() : "General");
        t.setPaymentMode(trimToNull(req.getPaymentMode()));
        t.setReferenceType(trimToNull(req.getReferenceType()) != null ? req.getReferenceType().trim() : REF_MANUAL);
        t.setReferenceId(trimToNull(req.getReferenceId()));
        t.setLocation(loc);
        t.setEventDate(d);
        t.setDescription(trimToNull(req.getDescription()));
        t.setCreatedBy(userId);
        t = moneyTransactionRepository.save(t);
        rebuildDayState(loc, d);
        return toTxDto(moneyTransactionRepository.findById(t.getId()).orElseThrow());
    }

    public MoneyTransactionResponseDTO updateTransaction(Long id, MoneyTransactionRequestDTO req, String location) {
        final String loc = normalizeLoc(location);
        MoneyTransaction t = moneyTransactionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        if (t.getIsDeleted() || !loc.equals(t.getLocation())) {
            throw new IllegalArgumentException("Transaction not found");
        }
        LocalDate oldDate = t.getEventDate();
        t.setTxType(req.getType());
        t.setAmount(req.getAmount().setScale(2, RoundingMode.HALF_UP));
        t.setCategory(req.getCategory());
        t.setPaymentMode(trimToNull(req.getPaymentMode()));
        t.setDescription(trimToNull(req.getDescription()));
        if (req.getEventDate() != null) {
            t.setEventDate(req.getEventDate());
        }
        moneyTransactionRepository.save(t);
        rebuildDayState(loc, oldDate);
        if (!oldDate.equals(t.getEventDate())) {
            rebuildDayState(loc, t.getEventDate());
        }
        return toTxDto(moneyTransactionRepository.findById(id).orElseThrow());
    }

    public void deleteTransaction(Long id, String location) {
        final String loc = normalizeLoc(location);
        MoneyTransaction t = moneyTransactionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        if (!loc.equals(t.getLocation())) {
            throw new IllegalArgumentException("Transaction not found");
        }
        if (Boolean.TRUE.equals(t.getIsDeleted())) {
            return;
        }
        t.setIsDeleted(true);
        moneyTransactionRepository.save(t);
        rebuildDayState(loc, t.getEventDate());
    }

    public CashbookListResponseDTO listCashbook(String location, LocalDate from, LocalDate to, MoneyTxType typeFilter) {
        final String loc = normalizeLoc(location);
        LocalDate f = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate t = to != null ? to : LocalDate.now();
        List<MoneyTransaction> txs = moneyTransactionRepository.listForLocationAndRange(loc, f, t);
        List<CashbookRowDTO> rows = new ArrayList<>();
        for (MoneyTransaction tx : txs) {
            if (Boolean.TRUE.equals(tx.getIsDeleted())) {
                continue;
            }
            if (typeFilter != null && tx.getTxType() != typeFilter) {
                continue;
            }
            rows.add(txToRow(tx));
        }
        for (LocalDate d = f; !d.isAfter(t); d = d.plusDays(1)) {
            List<BudgetManualAdjustment> mans = budgetManualAdjustmentRepository.findByLocationAndBudgetDateOrderByCreatedAtAsc(
                    loc, d);
            for (BudgetManualAdjustment m : mans) {
                rows.add(adjToRow(m));
            }
        }
        rows.sort(Comparator
                .comparing(CashbookRowDTO::getEventDate)
                .thenComparing(CashbookRowDTO::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(r -> r.getRowKind() + r.getId()));

        TodayBudgetDTO summary = buildTodayDto(loc, LocalDate.now());
        CashbookListResponseDTO out = new CashbookListResponseDTO();
        out.setRows(rows);
        out.setSummary(summary);
        return out;
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public TodayBudgetDTO getTodayBalance(String location, LocalDate date) {
        return buildTodayDto(normalizeLoc(location), date != null ? date : LocalDate.now());
    }

    public TodayBudgetDTO updateBudgetManually(ManualBudgetUpdateRequestDTO req, String location, Long userId) {
        final String loc = normalizeLoc(location);
        LocalDate d = req.getBudgetDate() != null ? req.getBudgetDate() : LocalDate.now();
        BigDecimal amt = req.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (amt.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount must be non-negative");
        }
        getOrCreateDayLocked(loc, d);

        BudgetManualAdjustment adj = new BudgetManualAdjustment();
        adj.setBudgetDate(d);
        adj.setLocation(loc);
        adj.setAdjustmentKind(req.getKind());
        adj.setAmount(amt);
        adj.setNote(trimToNull(req.getNote()));
        adj.setCreatedBy(userId);
        adj.setCreatedAt(LocalDateTime.now());
        budgetManualAdjustmentRepository.save(adj);

        rebuildDayState(loc, d);
        return buildTodayDto(loc, d);
    }

    /** Sync from legacy expense row (EXPENSE type in cashbook). */
    public void syncFromExpense(Expense expense, Long userId) {
        if (expense == null || expense.getId() == null) {
            return;
        }
        final String loc = normalizeLoc(expense.getLocation());
        LocalDate d = expense.getDate() != null ? expense.getDate() : LocalDate.now();
        getOrCreateDayLocked(loc, d);
        String refId = String.valueOf(expense.getId());
        Optional<MoneyTransaction> existing = moneyTransactionRepository.findByReferenceTypeAndReferenceIdAndIsDeletedFalse(
                REF_EXPENSE, refId);
        MoneyTransaction t = existing.orElseGet(MoneyTransaction::new);
        t.setTxType(MoneyTxType.EXPENSE);
        t.setAmount(expense.getAmount().setScale(2, RoundingMode.HALF_UP));
        t.setCategory(expense.getCategory() != null ? expense.getCategory() : "Expense");
        t.setPaymentMode(trimToNull(expense.getPaymentMethod()));
        t.setReferenceType(REF_EXPENSE);
        t.setReferenceId(refId);
        t.setLocation(loc);
        t.setEventDate(d);
        t.setDescription(trimToNull(expense.getDescription()));
        t.setIsDeleted(false);
        t.setCreatedBy(userId);
        if (t.getId() == null) {
            t.setCreatedAt(expense.getCreatedAt() != null ? expense.getCreatedAt() : LocalDateTime.now());
        }
        moneyTransactionRepository.save(t);
        rebuildDayState(loc, d);
    }

    public void removeSyncedExpense(Long expenseId, String location, LocalDate expenseDate) {
        if (expenseId == null) {
            return;
        }
        final String loc = normalizeLoc(location);
        LocalDate d = expenseDate != null ? expenseDate : LocalDate.now();
        moneyTransactionRepository.findByReferenceTypeAndReferenceIdAndIsDeletedFalse(REF_EXPENSE, String.valueOf(expenseId))
                .ifPresent(tx -> {
                    tx.setIsDeleted(true);
                    moneyTransactionRepository.save(tx);
                });
        rebuildDayState(loc, d);
    }

    /** Recompute balances for a calendar day (e.g. after expense date change). */
    public void rebuildDay(String location, LocalDate d) {
        if (d == null) {
            return;
        }
        rebuildDayState(normalizeLoc(location), d);
    }

    public void syncBillPayment(BillPaymentLedgerSyncEvent event) {
        if (event == null || event.location() == null || event.location().isBlank()) {
            return;
        }
        if (event.paymentMode() != BillPaymentMode.CASH && event.paymentMode() != BillPaymentMode.UPI) {
            return;
        }
        final String loc = normalizeLoc(event.location());
        LocalDate d = event.paymentDate() != null ? event.paymentDate() : LocalDate.now();
        String refId = String.valueOf(event.paymentId());
        if (!event.active()) {
            moneyTransactionRepository.findByReferenceTypeAndReferenceIdAndIsDeletedFalse(REF_BILL_PAYMENT, refId)
                    .ifPresent(tx -> {
                        tx.setIsDeleted(true);
                        moneyTransactionRepository.save(tx);
                    });
            rebuildDayState(loc, d);
            return;
        }
        if (event.amount() == null || event.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        getOrCreateDayLocked(loc, d);
        BigDecimal amt = event.amount().setScale(2, RoundingMode.HALF_UP);
        Optional<MoneyTransaction> existing = moneyTransactionRepository.findByReferenceTypeAndReferenceIdAndIsDeletedFalse(
                REF_BILL_PAYMENT, refId);
        MoneyTransaction t = existing.orElseGet(MoneyTransaction::new);
        t.setTxType(MoneyTxType.INCOME);
        t.setAmount(amt);
        t.setCategory("Bill payment");
        t.setPaymentMode(event.paymentMode().name());
        t.setReferenceType(REF_BILL_PAYMENT);
        t.setReferenceId(refId);
        t.setLocation(loc);
        t.setEventDate(d);
        t.setDescription("Bill #" + event.billId());
        t.setIsDeleted(false);
        moneyTransactionRepository.save(t);
        rebuildDayState(loc, d);
    }

    private void rebuildDayState(String loc, LocalDate d) {
        LocationDayBudget day = locationDayBudgetRepository.findByBudgetDateAndLocationForUpdate(d, loc)
                .orElse(null);
        if (day == null) {
            return;
        }
        BigDecimal running = day.getOpeningBalance() != null
                ? day.getOpeningBalance().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal manualAcc = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        List<BudgetManualAdjustment> mans = budgetManualAdjustmentRepository.findByLocationAndBudgetDateOrderByCreatedAtAsc(loc,
                d);
        List<MoneyTransaction> txs = moneyTransactionRepository.listForLocationAndDay(loc, d);

        List<TimelineEntry> merged = new ArrayList<>();
        long s = 0;
        for (BudgetManualAdjustment m : mans) {
            merged.add(new TimelineEntry(m.getCreatedAt() != null ? m.getCreatedAt() : LocalDateTime.MIN, s++, m, null));
        }
        for (MoneyTransaction t : txs) {
            if (Boolean.TRUE.equals(t.getIsDeleted())) {
                continue;
            }
            merged.add(new TimelineEntry(t.getCreatedAt() != null ? t.getCreatedAt() : LocalDateTime.MIN, s++, null, t));
        }
        merged.sort(Comparator.comparing((TimelineEntry e) -> e.ts).thenComparing(e -> e.seq));

        for (TimelineEntry m : merged) {
            if (m.adj != null) {
                switch (m.adj.getAdjustmentKind()) {
                    case ADD -> {
                        running = running.add(m.adj.getAmount());
                        manualAcc = manualAcc.add(m.adj.getAmount());
                    }
                    case SUBTRACT -> {
                        running = running.subtract(m.adj.getAmount());
                        manualAcc = manualAcc.subtract(m.adj.getAmount());
                    }
                    case SET_BALANCE -> {
                        BigDecimal target = m.adj.getAmount().setScale(2, RoundingMode.HALF_UP);
                        manualAcc = manualAcc.add(target.subtract(running));
                        running = target;
                    }
                }
                running = running.setScale(2, RoundingMode.HALF_UP);
                manualAcc = manualAcc.setScale(2, RoundingMode.HALF_UP);
                m.adj.setBalanceAfter(running);
                budgetManualAdjustmentRepository.save(m.adj);
            } else if (m.tx != null) {
                BigDecimal a = m.tx.getAmount().setScale(2, RoundingMode.HALF_UP);
                running = running.add(m.tx.getTxType() == MoneyTxType.INCOME ? a : a.negate());
                running = running.setScale(2, RoundingMode.HALF_UP);
                m.tx.setBalanceAfter(running);
                moneyTransactionRepository.save(m.tx);
            }
        }
        day.setManualAdjustmentTotal(manualAcc.setScale(2, RoundingMode.HALF_UP));
        day.setCurrentBalance(running.setScale(2, RoundingMode.HALF_UP));
        locationDayBudgetRepository.save(day);
    }

    private LocationDayBudget getOrCreateDayLocked(String loc, LocalDate d) {
        Optional<LocationDayBudget> locked = locationDayBudgetRepository.findByBudgetDateAndLocationForUpdate(d, loc);
        if (locked.isPresent()) {
            return locked.get();
        }
        LocationDayBudget row = new LocationDayBudget();
        row.setBudgetDate(d);
        row.setLocation(loc);
        BigDecimal opening = resolveOpeningBalance(loc, d);
        row.setOpeningBalance(opening);
        row.setManualAdjustmentTotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        row.setCurrentBalance(opening);
        try {
            locationDayBudgetRepository.saveAndFlush(row);
        } catch (DataIntegrityViolationException ignored) {
            // concurrent create
        }
        return locationDayBudgetRepository.findByBudgetDateAndLocationForUpdate(d, loc)
                .orElseThrow(() -> new IllegalStateException("Could not initialize day budget row"));
    }

    private BigDecimal resolveOpeningBalance(String loc, LocalDate d) {
        Optional<LocationDayBudget> prev = locationDayBudgetRepository
                .findFirstByLocationAndBudgetDateBeforeOrderByBudgetDateDesc(loc, d);
        if (prev.isPresent() && prev.get().getCurrentBalance() != null) {
            return prev.get().getCurrentBalance().max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        }
        return dailyBudgetRepository.findFirstByLocationAndUserIdIsNull(loc)
                .map(b -> b.getAmount() != null ? b.getAmount() : BigDecimal.ZERO)
                .or(() -> dailyBudgetRepository.findByLocation(loc).map(b -> b.getAmount() != null ? b.getAmount() : BigDecimal.ZERO))
                .orElse(BigDecimal.ZERO)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private TodayBudgetDTO buildTodayDto(String loc, LocalDate d) {
        TodayBudgetDTO dto = new TodayBudgetDTO();
        dto.setDate(d);
        dto.setLocation(loc);
        locationDayBudgetRepository.findByBudgetDateAndLocation(d, loc).ifPresentOrElse(day -> {
            dto.setOpeningBalance(day.getOpeningBalance());
            dto.setManualAdjustmentTotal(day.getManualAdjustmentTotal());
            dto.setCurrentBalance(day.getCurrentBalance());
            dto.setClosingBalance(day.getClosingBalance());
        }, () -> {
            dto.setOpeningBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            dto.setManualAdjustmentTotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            dto.setCurrentBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        });
        return dto;
    }

    private static final class TimelineEntry {
        final LocalDateTime ts;
        final long seq;
        final BudgetManualAdjustment adj;
        final MoneyTransaction tx;

        TimelineEntry(LocalDateTime ts, long seq, BudgetManualAdjustment adj, MoneyTransaction tx) {
            this.ts = ts;
            this.seq = seq;
            this.adj = adj;
            this.tx = tx;
        }
    }

    private static String normalizeLoc(String location) {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("Location is required");
        }
        return location.trim();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static MoneyTransactionResponseDTO toTxDto(MoneyTransaction t) {
        MoneyTransactionResponseDTO d = new MoneyTransactionResponseDTO();
        d.setId(t.getId());
        d.setType(t.getTxType());
        d.setAmount(t.getAmount());
        d.setCategory(t.getCategory());
        d.setPaymentMode(t.getPaymentMode());
        d.setReferenceType(t.getReferenceType());
        d.setReferenceId(t.getReferenceId());
        d.setLocation(t.getLocation());
        d.setEventDate(t.getEventDate());
        d.setDescription(t.getDescription());
        d.setBalanceAfter(t.getBalanceAfter());
        d.setCreatedAt(t.getCreatedAt());
        return d;
    }

    private static CashbookRowDTO txToRow(MoneyTransaction t) {
        CashbookRowDTO r = new CashbookRowDTO();
        r.setId(t.getId());
        r.setRowKind("TRANSACTION");
        r.setEventDate(t.getEventDate());
        r.setCreatedAt(t.getCreatedAt());
        r.setDisplayType(t.getTxType().name());
        r.setCategory(t.getCategory());
        r.setAmount(t.getAmount());
        r.setSignedAmount(t.getTxType() == MoneyTxType.INCOME ? t.getAmount() : t.getAmount().negate());
        r.setPaymentMode(t.getPaymentMode());
        r.setDescription(t.getDescription());
        r.setBalanceAfter(t.getBalanceAfter());
        r.setReferenceType(t.getReferenceType());
        r.setReferenceId(t.getReferenceId());
        return r;
    }

    private static CashbookRowDTO adjToRow(BudgetManualAdjustment m) {
        CashbookRowDTO r = new CashbookRowDTO();
        r.setId(m.getId());
        r.setRowKind("MANUAL");
        r.setEventDate(m.getBudgetDate());
        r.setCreatedAt(m.getCreatedAt());
        r.setDisplayType(switch (m.getAdjustmentKind()) {
            case ADD -> "MANUAL_ADD";
            case SUBTRACT -> "MANUAL_SUB";
            case SET_BALANCE -> "MANUAL_SET";
        });
        r.setCategory("Manual adjustment");
        r.setAmount(m.getAmount());
        r.setSignedAmount(switch (m.getAdjustmentKind()) {
            case ADD -> m.getAmount();
            case SUBTRACT -> m.getAmount().negate();
            case SET_BALANCE -> BigDecimal.ZERO;
        });
        r.setPaymentMode("-");
        r.setDescription(m.getNote());
        r.setBalanceAfter(m.getBalanceAfter());
        return r;
    }
}
