package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.EmployeePayrollSummaryDTO;
import com.katariastoneworld.apis.dto.PayrollAdvanceRequestDTO;
import com.katariastoneworld.apis.dto.PayrollSalarySettlementRequestDTO;
import com.katariastoneworld.apis.entity.BillPaymentMode;
import com.katariastoneworld.apis.entity.Employee;
import com.katariastoneworld.apis.entity.EmployeePayrollLedgerEntry;
import com.katariastoneworld.apis.entity.EmployeePayrollLedgerEntry.EventType;
import com.katariastoneworld.apis.entity.ExpenseCategory;
import com.katariastoneworld.apis.entity.Expense;
import com.katariastoneworld.apis.entity.ReferenceType;
import com.katariastoneworld.apis.repository.EmployeePayrollLedgerRepository;
import com.katariastoneworld.apis.repository.EmployeeRepository;
import com.katariastoneworld.apis.repository.ExpenseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class PayrollLedgerService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal EPS = new BigDecimal("0.01");

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmployeePayrollLedgerRepository ledgerRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private DailyBudgetService dailyBudgetService;

    public EmployeePayrollLedgerEntry recordAdvance(Long employeeId, PayrollAdvanceRequestDTO req, String location, Long actorUserId) {
        Employee emp = employeeRepository.findByIdAndLocation(employeeId, location)
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + employeeId));
        BigDecimal amt = scale2(req.getAmount());
        if (amt.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        LocalDate d = req.getDate() != null ? req.getDate() : LocalDate.now();
        BillPaymentMode mode = parsePaymentMode(req.getPaymentMode());

        EmployeePayrollLedgerEntry e = new EmployeePayrollLedgerEntry();
        e.setEmployeeId(emp.getId());
        e.setLocation(location);
        e.setEventType(EventType.ADVANCE_GIVEN);
        e.setAmount(amt);
        e.setPaymentMode(mode);
        e.setEventDate(d);
        e.setNotes(trim(req.getNotes()));
        e.setCreatedBy(actorUserId);
        EmployeePayrollLedgerEntry saved = ledgerRepository.save(e);

        // Mirror into expenses for cash/budget + reporting consistency.
        Expense ex = new Expense();
        ex.setType("advance");
        ex.setCategory("employee");
        ex.setDate(d);
        ex.setAmount(amt);
        ex.setPaymentMethod(toLegacyExpensePaymentMethod(mode));
        ex.setEmployeeId(emp.getId());
        ex.setEmployeeName(emp.getEmployeeName());
        ex.setSettled(false);
        ex.setDescription("Employee advance: " + emp.getEmployeeName() + (req.getNotes() != null ? " - " + req.getNotes() : ""));
        ex.setLocation(location);
        ex.setExpenseCategory(ExpenseCategory.SALARY);
        ex.setReferenceType(ReferenceType.PAYROLL);
        ex.setReferenceId(String.valueOf(saved.getId()));
        expenseRepository.save(ex);

        if (LocalDate.now().equals(d)) {
            dailyBudgetService.adjustRemainingForDailyExpense(location, amt.negate());
        }
        return saved;
    }

    public EmployeePayrollSummaryDTO settleSalaryMonth(Long employeeId, PayrollSalarySettlementRequestDTO req, String location,
            Long actorUserId) {
        Employee emp = employeeRepository.findByIdAndLocation(employeeId, location)
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + employeeId));
        String month = normalizeMonth(req.getMonth());
        LocalDate d = req.getDate() != null ? req.getDate() : LocalDate.now();
        BillPaymentMode mode = parsePaymentMode(req.getPaymentMode());

        BigDecimal salaryDue = scale2(emp.getSalaryAmount());
        if (salaryDue.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("Employee salary must be positive");
        }

        BigDecimal alreadyApplied = scale2(ledgerRepository.sumAmountByEmployeeTypeInMonth(location, employeeId, EventType.ADVANCE_APPLIED, month));
        BigDecimal alreadyPaidCash = scale2(ledgerRepository.sumAmountByEmployeeTypeInMonth(location, employeeId, EventType.SALARY_CASH_PAID, month));
        BigDecimal remainingDue = salaryDue.subtract(alreadyApplied).subtract(alreadyPaidCash).max(ZERO).setScale(2, RoundingMode.HALF_UP);
        if (remainingDue.compareTo(ZERO) <= 0) {
            return buildSummary(emp, location, month);
        }

        BigDecimal advanceBalance = currentAdvanceBalance(location, employeeId);
        BigDecimal applyNow = advanceBalance.min(remainingDue).setScale(2, RoundingMode.HALF_UP);
        if (applyNow.compareTo(ZERO) > 0) {
            EmployeePayrollLedgerEntry apply = new EmployeePayrollLedgerEntry();
            apply.setEmployeeId(emp.getId());
            apply.setLocation(location);
            apply.setEventType(EventType.ADVANCE_APPLIED);
            apply.setAmount(applyNow);
            apply.setPaymentMode(null); // no cash movement
            apply.setEventDate(d);
            apply.setMonth(month);
            apply.setNotes(trim(req.getNotes()));
            apply.setCreatedBy(actorUserId);
            ledgerRepository.save(apply);
        }

        remainingDue = remainingDue.subtract(applyNow).max(ZERO).setScale(2, RoundingMode.HALF_UP);

        BigDecimal desiredCashPaid = req.getCashPaidAmount() != null ? scale2(req.getCashPaidAmount()) : remainingDue;
        if (desiredCashPaid.compareTo(ZERO) < 0) {
            throw new IllegalArgumentException("cashPaidAmount cannot be negative");
        }
        if (desiredCashPaid.subtract(remainingDue).compareTo(EPS) > 0) {
            throw new IllegalArgumentException("Cash paid cannot exceed remaining salary due for month");
        }

        if (desiredCashPaid.compareTo(ZERO) > 0) {
            EmployeePayrollLedgerEntry pay = new EmployeePayrollLedgerEntry();
            pay.setEmployeeId(emp.getId());
            pay.setLocation(location);
            pay.setEventType(EventType.SALARY_CASH_PAID);
            pay.setAmount(desiredCashPaid);
            pay.setPaymentMode(mode);
            pay.setEventDate(d);
            pay.setMonth(month);
            pay.setNotes(trim(req.getNotes()));
            pay.setCreatedBy(actorUserId);
            ledgerRepository.save(pay);

            Expense ex = new Expense();
            ex.setType("salary");
            ex.setCategory("salary");
            ex.setDate(d);
            ex.setAmount(desiredCashPaid);
            ex.setPaymentMethod(toLegacyExpensePaymentMethod(mode));
            ex.setEmployeeId(emp.getId());
            ex.setEmployeeName(emp.getEmployeeName());
            ex.setMonth(month);
            ex.setSettled(true);
            ex.setDescription("Salary payment: " + emp.getEmployeeName() + " - " + month);
            ex.setLocation(location);
            ex.setExpenseCategory(ExpenseCategory.SALARY);
            ex.setReferenceType(ReferenceType.PAYROLL);
            ex.setReferenceId(String.valueOf(pay.getId()));
            expenseRepository.save(ex);

            if (LocalDate.now().equals(d)) {
                dailyBudgetService.adjustRemainingForDailyExpense(location, desiredCashPaid.negate());
            }
        }

        return buildSummary(emp, location, month);
    }

    public List<EmployeePayrollSummaryDTO> getMonthlySummaries(String location, String month) {
        String m = normalizeMonth(month);
        List<Employee> emps = employeeRepository.findByLocation(location);
        List<EmployeePayrollSummaryDTO> out = new ArrayList<>();
        for (Employee e : emps) {
            out.add(buildSummary(e, location, m));
        }
        return out;
    }

    public List<EmployeePayrollLedgerEntry> getLedger(Long employeeId, LocalDate from, LocalDate to, String location) {
        employeeRepository.findByIdAndLocation(employeeId, location)
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + employeeId));
        LocalDate f = from != null ? from : LocalDate.now().minusMonths(6);
        LocalDate t = to != null ? to : LocalDate.now();
        return ledgerRepository.findByEmployeeIdAndEventDateBetweenOrderByEventDateAscIdAsc(employeeId, f, t);
    }

    private EmployeePayrollSummaryDTO buildSummary(Employee emp, String location, String month) {
        LocalDate first = LocalDate.parse(month + "-01");
        BigDecimal advGivenBefore = scale2(ledgerRepository.sumAmountByEmployeeTypeBefore(location, emp.getId(), EventType.ADVANCE_GIVEN, first));
        BigDecimal advAppliedBefore = scale2(ledgerRepository.sumAmountByEmployeeTypeBefore(location, emp.getId(), EventType.ADVANCE_APPLIED, first));
        BigDecimal advBalStart = advGivenBefore.subtract(advAppliedBefore).max(ZERO).setScale(2, RoundingMode.HALF_UP);

        BigDecimal advGivenMonth = scale2(ledgerRepository.sumAmountByEmployeeTypeInMonth(location, emp.getId(), EventType.ADVANCE_GIVEN, month));
        BigDecimal advAppliedMonth = scale2(ledgerRepository.sumAmountByEmployeeTypeInMonth(location, emp.getId(), EventType.ADVANCE_APPLIED, month));
        BigDecimal cashPaidMonth = scale2(ledgerRepository.sumAmountByEmployeeTypeInMonth(location, emp.getId(), EventType.SALARY_CASH_PAID, month));

        BigDecimal salaryDue = scale2(emp.getSalaryAmount());
        // Net advance balance after this month's ledger rows (ADVANCE_GIVEN / ADVANCE_APPLIED).
        BigDecimal advBalEnd = advBalStart.add(advGivenMonth).subtract(advAppliedMonth).max(ZERO).setScale(2, RoundingMode.HALF_UP);
        // Salary not yet covered by formal ADVANCE_APPLIED + SALARY_CASH_PAID for this month.
        BigDecimal unpaidBeforeImplicitAdvance = salaryDue.subtract(advAppliedMonth).subtract(cashPaidMonth).max(ZERO).setScale(2, RoundingMode.HALF_UP);
        boolean hasSettlementAgainstSalary = advAppliedMonth.compareTo(ZERO) > 0 || cashPaidMonth.compareTo(ZERO) > 0;

        BigDecimal remaining;
        if (hasSettlementAgainstSalary) {
            // Ledger already ties some advance/cash to salary — offset only up to unpaid (no double count).
            BigDecimal offsetFromAdvance = advBalEnd.min(unpaidBeforeImplicitAdvance).setScale(2, RoundingMode.HALF_UP);
            remaining = unpaidBeforeImplicitAdvance.subtract(offsetFromAdvance).max(ZERO).setScale(2, RoundingMode.HALF_UP);
        } else {
            // No settlement yet: net payable = salary due − advance pool (negative = over-advance vs this month's salary).
            remaining = unpaidBeforeImplicitAdvance.subtract(advBalEnd).setScale(2, RoundingMode.HALF_UP);
        }

        String status;
        if (remaining.compareTo(ZERO) < 0) {
            status = "OVER_ADVANCE";
        } else if (remaining.compareTo(EPS) <= 0) {
            status = "PAID";
        } else {
            status = "PARTIAL";
        }
        return new EmployeePayrollSummaryDTO(
                emp.getId(),
                emp.getEmployeeName(),
                month,
                salaryDue.doubleValue(),
                advBalStart.doubleValue(),
                advGivenMonth.doubleValue(),
                advAppliedMonth.doubleValue(),
                cashPaidMonth.doubleValue(),
                remaining.doubleValue(),
                advBalEnd.doubleValue(),
                status
        );
    }

    private BigDecimal currentAdvanceBalance(String location, Long employeeId) {
        BigDecimal given = scale2(ledgerRepository.sumAmountByEmployeeTypeAllTime(location, employeeId, EventType.ADVANCE_GIVEN));
        BigDecimal applied = scale2(ledgerRepository.sumAmountByEmployeeTypeAllTime(location, employeeId, EventType.ADVANCE_APPLIED));
        return given.subtract(applied).max(ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale2(BigDecimal v) {
        return (v == null ? ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    private static String normalizeMonth(String month) {
        String m = month == null ? "" : month.trim();
        if (!m.matches("^\\d{4}-\\d{2}$")) {
            throw new IllegalArgumentException("Month must be YYYY-MM");
        }
        return m;
    }

    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static BillPaymentMode parsePaymentMode(String raw) {
        if (raw == null || raw.isBlank()) return BillPaymentMode.CASH;
        String r = raw.trim();
        // accept expense-style values
        if (Objects.equals(r.toLowerCase(), "cash")) return BillPaymentMode.CASH;
        if (Objects.equals(r.toLowerCase(), "upi")) return BillPaymentMode.UPI;
        if (Objects.equals(r.toLowerCase(), "bank")) return BillPaymentMode.BANK_TRANSFER;
        if (Objects.equals(r.toLowerCase(), "cheque") || Objects.equals(r.toLowerCase(), "check")) return BillPaymentMode.CHEQUE;
        if (Objects.equals(r.toLowerCase(), "other") || Objects.equals(r.toLowerCase(), "card")) return BillPaymentMode.OTHER;
        return BillPaymentMode.parseFlexible(r);
    }

    private static String toLegacyExpensePaymentMethod(BillPaymentMode mode) {
        if (mode == null) return "cash";
        return switch (mode) {
            case CASH -> "cash";
            case UPI -> "upi";
            case BANK_TRANSFER -> "bank";
            case CHEQUE -> "cheque";
            case OTHER -> "other";
        };
    }
}

