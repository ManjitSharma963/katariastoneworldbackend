package com.katariastoneworld.apis.service.audit;

import com.katariastoneworld.apis.dto.BalanceSummaryDTO;
import com.katariastoneworld.apis.service.BalanceSummaryService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class AuditReportSummaryService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final JdbcTemplate jdbcTemplate;
    private final BalanceSummaryService balanceSummaryService;

    public AuditReportSummaryService(JdbcTemplate jdbcTemplate, BalanceSummaryService balanceSummaryService) {
        this.jdbcTemplate = jdbcTemplate;
        this.balanceSummaryService = balanceSummaryService;
    }

    public Map<String, String> buildDashboardSummary(String location) {
        Map<String, String> summary = new LinkedHashMap<>();
        String loc = location != null ? location.trim() : "";

        summary.put("Total Customers", formatCount(countWhere("customers", "location = ?", loc)));
        summary.put("Total Suppliers", formatCount(countAll("suppliers")));
        summary.put("Total Products", formatCount(countWhere("products", "location = ?", loc)));
        summary.put("Stock Value (Inventory)", formatMoney(stockValue(loc)));
        summary.put("Client Purchases (Total)", formatMoney(clientPurchaseTotal(loc)));
        summary.put("Total Sales (All Bills)", formatMoney(totalSales(loc)));
        summary.put("Total Expenses", formatMoney(totalExpenses(loc)));
        summary.put("Loan Payables (Outstanding)", formatMoney(loanPayables()));
        summary.put("Receivables (Lend Outstanding)", formatMoney(receivablesOutstanding()));
        summary.put("Customer Advance Balance", formatMoney(customerAdvanceRemaining(loc)));

        BalanceSummaryDTO balance = balanceSummaryService.getSummary(loc);
        summary.put("Cash Balance (Cash + UPI)", formatMoney(balance.getInHand()));
        summary.put("Bank Balance", formatMoney(balance.getBank()));
        summary.put("Combined Cash + Bank", formatMoney(balance.getTotal()));

        BigDecimal sales = totalSales(loc);
        BigDecimal expenses = totalExpenses(loc);
        summary.put("Net Profit / Loss (Sales − Expenses)", formatMoney(sales.subtract(expenses)));
        return summary;
    }

    public long countAllRecordsInTables(Iterable<String> tables) {
        long total = 0;
        for (String table : tables) {
            total += countAll(table);
        }
        return total;
    }

    public long countAll(String table) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + quote(table), Long.class);
        return count != null ? count : 0L;
    }

    private long countWhere(String table, String whereClause, String location) {
        if (location == null || location.isBlank()) {
            return countAll(table);
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + quote(table) + " WHERE " + whereClause,
                Long.class,
                location);
        return count != null ? count : 0L;
    }

    private BigDecimal stockValue(String location) {
        if (location == null || location.isBlank()) {
            return scalar("SELECT COALESCE(SUM(total_sqft_stock * COALESCE(price_per_sqft, 0)), 0) FROM products");
        }
        return scalar(
                "SELECT COALESCE(SUM(total_sqft_stock * COALESCE(price_per_sqft, 0)), 0) FROM products WHERE location = ?",
                location);
    }

    private BigDecimal clientPurchaseTotal(String location) {
        if (location.isBlank()) {
            return scalar("SELECT COALESCE(SUM(total_amount), 0) FROM client_purchases");
        }
        return scalar("SELECT COALESCE(SUM(total_amount), 0) FROM client_purchases WHERE location = ?", location);
    }

    private BigDecimal totalSales(String location) {
        BigDecimal gst = location.isBlank()
                ? scalar("SELECT COALESCE(SUM(total_amount), 0) FROM bills_gst")
                : scalar("SELECT COALESCE(SUM(total_amount), 0) FROM bills_gst WHERE location = ?", location);
        BigDecimal nonGst = location.isBlank()
                ? scalar("SELECT COALESCE(SUM(total_amount), 0) FROM bills_non_gst")
                : scalar("SELECT COALESCE(SUM(total_amount), 0) FROM bills_non_gst WHERE location = ?", location);
        return scale2(gst.add(nonGst));
    }

    private BigDecimal totalExpenses(String location) {
        if (location.isBlank()) {
            return scalar("SELECT COALESCE(SUM(amount), 0) FROM expenses");
        }
        return scalar("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE location = ?", location);
    }

    private BigDecimal loanPayables() {
        return scalar("""
                SELECT COALESCE(SUM(CASE
                    WHEN entry_type = 'RECEIPT' THEN amount
                    WHEN entry_type = 'REPAYMENT' THEN -amount
                    ELSE 0 END), 0)
                FROM loan_ledger_entries""");
    }

    private BigDecimal receivablesOutstanding() {
        return scalar("""
                SELECT COALESCE(SUM(CASE
                    WHEN entry_type = 'DISBURSEMENT' THEN amount
                    WHEN entry_type = 'REPAYMENT_RECEIVED' THEN -amount
                    ELSE 0 END), 0)
                FROM receivable_ledger_entries""");
    }

    private BigDecimal customerAdvanceRemaining(String location) {
        if (location.isBlank()) {
            return scalar("SELECT COALESCE(SUM(remaining_amount), 0) FROM customer_advance");
        }
        return scalar(
                "SELECT COALESCE(SUM(ca.remaining_amount), 0) FROM customer_advance ca " +
                        "JOIN customers c ON c.id = ca.customer_id WHERE c.location = ?",
                location);
    }

    private BigDecimal scalar(String sql, Object... args) {
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
        return scale2(value != null ? value : ZERO);
    }

    private static BigDecimal scale2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String formatCount(long count) {
        return String.format(Locale.ROOT, "%,d", count);
    }

    private static String formatMoney(BigDecimal amount) {
        return String.format(Locale.ROOT, "₹%,.2f", amount != null ? amount : ZERO);
    }

    private static String quote(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }
}
