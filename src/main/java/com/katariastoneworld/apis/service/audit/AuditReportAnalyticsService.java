package com.katariastoneworld.apis.service.audit;

import com.katariastoneworld.apis.dto.BalanceSummaryDTO;
import com.katariastoneworld.apis.service.BalanceSummaryService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AuditReportAnalyticsService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final DateTimeFormatter PERIOD_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH);

    private final JdbcTemplate jdbcTemplate;
    private final AuditReportSummaryService summaryService;
    private final BalanceSummaryService balanceSummaryService;
    private final AuditReportModuleCatalog moduleCatalog;

    public AuditReportAnalyticsService(JdbcTemplate jdbcTemplate,
                                       AuditReportSummaryService summaryService,
                                       BalanceSummaryService balanceSummaryService,
                                       AuditReportModuleCatalog moduleCatalog) {
        this.jdbcTemplate = jdbcTemplate;
        this.summaryService = summaryService;
        this.balanceSummaryService = balanceSummaryService;
        this.moduleCatalog = moduleCatalog;
    }

    public AuditReportSnapshot buildSnapshot(AuditReportJob job,
                                             List<String> orderedTables,
                                             LocalDateTime generatedAt,
                                             long generationStartMs) {
        String loc = job.getLocation() != null ? job.getLocation().trim() : "";
        long grandTotal = summaryService.countAllRecordsInTables(orderedTables);

        BalanceSummaryDTO balance = balanceSummaryService.getSummary(loc);
        BigDecimal sales = totalSales(loc);
        BigDecimal expenses = totalExpenses(loc);
        BigDecimal stock = stockValue(loc);
        BigDecimal receivables = receivablesOutstanding();
        BigDecimal payables = loanPayables().add(clientPurchaseTotal(loc));

        List<AuditReportSnapshot.KpiCard> kpis = buildKpis(loc, balance, sales, expenses, stock, receivables, payables);
        List<AuditReportSnapshot.InsightCard> insights = buildInsights(loc);
        Map<String, String> financial = buildFinancialSummary(balance, sales, expenses, stock, receivables, payables);
        Map<String, String> inventory = buildInventoryAnalytics(loc);
        List<AuditReportSnapshot.ModuleSummary> moduleSummaries = buildModuleSummaries(loc, orderedTables);

        String checksum = checksum(job.getJobId(), grandTotal, generatedAt);

        return AuditReportSnapshot.builder()
                .reportId(job.getJobId())
                .databaseVersion(databaseVersion())
                .branch(loc.isBlank() ? "All Branches" : loc)
                .reportPeriod("Point-in-time snapshot as of " + PERIOD_FMT.format(generatedAt))
                .checksum(checksum)
                .kpis(kpis)
                .insights(insights)
                .financialSummary(financial)
                .inventoryAnalytics(inventory)
                .paymentModeChart(paymentModeDistribution(loc))
                .monthlySalesChart(monthlySales(loc, 6))
                .expenseCategoryChart(expenseByCategory(loc))
                .moduleSummaries(moduleSummaries)
                .build();
    }

    private List<AuditReportSnapshot.KpiCard> buildKpis(String loc,
                                                        BalanceSummaryDTO balance,
                                                        BigDecimal sales,
                                                        BigDecimal expenses,
                                                        BigDecimal stock,
                                                        BigDecimal receivables,
                                                        BigDecimal payables) {
        List<AuditReportSnapshot.KpiCard> cards = new ArrayList<>();
        cards.add(kpi("Total Customers", formatCount(countCustomers(loc)), "primary"));
        cards.add(kpi("Total Suppliers", formatCount(countAll("suppliers")), "teal"));
        cards.add(kpi("Total Products", formatCount(countProducts(loc)), "primary"));
        cards.add(kpi("Total Bills", formatCount(countBills(loc)), "gold"));
        cards.add(kpi("Total Sales", formatMoney(sales), "success"));
        cards.add(kpi("Total Purchases", formatMoney(clientPurchaseTotal(loc)), "teal"));
        cards.add(kpi("Cash Balance", formatMoney(balance.getInHand()), "success"));
        cards.add(kpi("Bank Balance", formatMoney(balance.getBank()), "success"));
        cards.add(kpi("Outstanding Receivable", formatMoney(receivables), "warning"));
        cards.add(kpi("Outstanding Payable", formatMoney(payables), "warning"));
        cards.add(kpi("Inventory Value", formatMoney(stock), "gold"));
        cards.add(kpi("Today's Sales", formatMoney(todaySales(loc)), "success"));
        cards.add(kpi("This Month Sales", formatMoney(monthSales(loc)), "success"));
        cards.add(kpi("Net Profit", formatMoney(sales.subtract(expenses)), sales.compareTo(expenses) >= 0 ? "success" : "warning"));
        cards.add(kpi("Total Expenses", formatMoney(expenses), "primary"));
        cards.add(kpi("Cancelled Bills", formatCount(cancelledBills(loc)), "warning"));
        cards.add(kpi("Returned Items", formatCount(countAll("bill_inventory_return_lines")), "warning"));
        cards.add(kpi("Low Stock Products", formatCount(lowStockProducts(loc)), "warning"));
        cards.add(kpi("Customer Advance", formatMoney(customerAdvanceRemaining(loc)), "teal"));
        return cards;
    }

    private List<AuditReportSnapshot.InsightCard> buildInsights(String loc) {
        List<AuditReportSnapshot.InsightCard> list = new ArrayList<>();
        addInsight(list, "Highest Sale", scalarLabel(
                "SELECT CONCAT(COALESCE(bill_number,'?'), ' — ', COALESCE(total_amount,0)) FROM bills_gst "
                        + locationWhere(loc, "location") + " ORDER BY total_amount DESC LIMIT 1", loc));
        addInsight(list, "Highest Purchase", scalarLabel(
                "SELECT CONCAT(COALESCE(client_name,'?'), ' — ', COALESCE(total_amount,0)) FROM client_purchases "
                        + locationWhere(loc, "location") + " ORDER BY total_amount DESC LIMIT 1", loc));
        addInsight(list, "Best Customer (Sales)", scalarLabel(
                "SELECT CONCAT(COALESCE(customer_name,'?'), ' — ', COALESCE(SUM(total_amount),0)) FROM bills_gst "
                        + locationWhere(loc, "location") + " GROUP BY customer_name ORDER BY SUM(total_amount) DESC LIMIT 1", loc));
        addInsight(list, "Most Sold Product Type", scalarLabel(
                "SELECT CONCAT(COALESCE(product_type,'?'), ' (', COUNT(*), ' SKUs)') FROM products "
                        + locationWhere(loc, "location") + " GROUP BY product_type ORDER BY COUNT(*) DESC LIMIT 1", loc));
        addInsight(list, "Most Sold Granite", scalarLabel(
                "SELECT COALESCE(name,'?') FROM products WHERE LOWER(product_type) LIKE '%granite%' "
                        + (loc.isBlank() ? "" : "AND location = ? ") + "ORDER BY total_sqft_stock DESC LIMIT 1", loc));
        addInsight(list, "Most Sold Marble", scalarLabel(
                "SELECT COALESCE(name,'?') FROM products WHERE LOWER(product_type) LIKE '%marble%' "
                        + (loc.isBlank() ? "" : "AND location = ? ") + "ORDER BY total_sqft_stock DESC LIMIT 1", loc));
        addInsight(list, "Average Bill Value", formatMoney(averageBillValue(loc)));
        addInsight(list, "Most Used Payment Method", scalarLabel(
                "SELECT COALESCE(payment_mode,'?') FROM bill_payments GROUP BY payment_mode ORDER BY COUNT(*) DESC LIMIT 1"));
        addInsight(list, "Top Revenue Product", scalarLabel(
                "SELECT CONCAT(COALESCE(name,'?'), ' — Stock value ', COALESCE(total_sqft_stock * price_per_sqft,0)) FROM products "
                        + locationWhere(loc, "location") + " ORDER BY total_sqft_stock * price_per_sqft DESC LIMIT 1", loc));
        addInsight(list, "Fast Moving (Recent Stock Out)", formatCount(recentStockOutCount(loc)));
        addInsight(list, "Out of Stock Products", formatCount(outOfStock(loc)));
        return list;
    }

    private Map<String, String> buildFinancialSummary(BalanceSummaryDTO balance,
                                                      BigDecimal sales,
                                                      BigDecimal expenses,
                                                      BigDecimal stock,
                                                      BigDecimal receivables,
                                                      BigDecimal payables) {
        Map<String, String> m = new LinkedHashMap<>();
        BigDecimal cash = balance.getInHand() != null ? balance.getInHand() : ZERO;
        BigDecimal bank = balance.getBank() != null ? balance.getBank() : ZERO;
        BigDecimal profit = sales.subtract(expenses);
        m.put("Cash & UPI", formatMoney(cash));
        m.put("Bank", formatMoney(bank));
        m.put("Inventory (Assets)", formatMoney(stock));
        m.put("Receivables", formatMoney(receivables));
        m.put("Payables & Liabilities", formatMoney(payables));
        m.put("Revenue (Total Sales)", formatMoney(sales));
        m.put("Expenses", formatMoney(expenses));
        m.put("Net Profit / Loss", formatMoney(profit));
        m.put("Combined Liquidity", formatMoney(cash.add(bank)));
        m.put("Net Worth (Est.)", formatMoney(cash.add(bank).add(stock).add(receivables).subtract(payables)));
        return m;
    }

    private Map<String, String> buildInventoryAnalytics(String loc) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Total SKUs", formatCount(countProducts(loc)));
        m.put("Inventory Value", formatMoney(stockValue(loc)));
        m.put("Low Stock Items", formatCount(lowStockProducts(loc)));
        m.put("Out of Stock", formatCount(outOfStock(loc)));
        m.put("Active Products", formatCount(countProductsActive(loc)));
        m.put("Reserved Stock Lines", formatCount(countAll("inventory_reservations")));
        m.put("Return Lines", formatCount(countAll("bill_inventory_return_lines")));
        m.put("Stock Movements (Transactions)", formatCount(countAll("inventory_transactions")));
        return m;
    }

    private List<AuditReportSnapshot.ModuleSummary> buildModuleSummaries(String loc, List<String> orderedTables) {
        List<AuditReportSnapshot.ModuleSummary> summaries = new ArrayList<>();
        for (AuditReportModuleCatalog.ModuleSection module : moduleCatalog.modules()) {
            List<String> tables = module.tables().stream().filter(orderedTables::contains).toList();
            if (tables.isEmpty()) {
                continue;
            }
            long records = summaryService.countAllRecordsInTables(tables);
            List<String> bullets = new ArrayList<>();
            bullets.add(String.format(Locale.ROOT, "%,d total records across %d tables", records, tables.size()));
            bullets.addAll(moduleSpecificBullets(module.id(), loc));
            summaries.add(AuditReportSnapshot.ModuleSummary.builder()
                    .moduleId(module.id())
                    .title(module.title())
                    .recordCount(records)
                    .bullets(bullets)
                    .build());
        }
        return summaries;
    }

    private List<String> moduleSpecificBullets(String moduleId, String loc) {
        List<String> b = new ArrayList<>();
        switch (moduleId) {
            case "customers" -> {
                b.add("Customers: " + formatCount(countCustomers(loc)));
                b.add("With advance balance: " + formatCount(customersWithAdvance(loc)));
            }
            case "sales" -> {
                b.add("GST bills: " + formatCount(countWhere("bills_gst", loc)));
                b.add("Non-GST bills: " + formatCount(countWhere("bills_non_gst", loc)));
                b.add("Cancelled: " + formatCount(cancelledBills(loc)));
            }
            case "inventory" -> {
                b.add("Low stock alerts: " + formatCount(lowStockProducts(loc)));
                b.add("Inventory value: " + formatMoney(stockValue(loc)));
            }
            case "expenses" -> b.add("Total expenses: " + formatMoney(totalExpenses(loc)));
            case "ledger" -> {
                BalanceSummaryDTO bal = balanceSummaryService.getSummary(loc);
                b.add("Cash+UPI: " + formatMoney(bal.getInHand()));
                b.add("Bank: " + formatMoney(bal.getBank()));
            }
            default -> { /* module intro only */ }
        }
        return b;
    }

    private List<AuditReportSnapshot.ChartBar> paymentModeDistribution(String loc) {
        return chartBars("""
                SELECT COALESCE(payment_mode, 'OTHER') AS lbl, COUNT(*) AS cnt
                FROM bill_payments GROUP BY COALESCE(payment_mode, 'OTHER')
                ORDER BY cnt DESC LIMIT 8""");
    }

    private List<AuditReportSnapshot.ChartBar> monthlySales(String loc, int months) {
        String sql = """
                SELECT DATE_FORMAT(bill_date, '%Y-%m') AS lbl, COALESCE(SUM(total_amount),0) AS amt
                FROM (
                    SELECT bill_date, total_amount FROM bills_gst """
                + (loc.isBlank() ? "" : " WHERE location = ? ")
                + """
                  UNION ALL
                    SELECT bill_date, total_amount FROM bills_non_gst """
                + (loc.isBlank() ? "" : " WHERE location = ? ")
                + """
                ) combined
                WHERE bill_date >= DATE_SUB(CURDATE(), INTERVAL """
                + months + " MONTH)"
                + " GROUP BY lbl ORDER BY lbl";
        if (loc.isBlank()) {
            return chartBarsAmount(sql);
        }
        return chartBarsAmount(sql, loc, loc);
    }

    private List<AuditReportSnapshot.ChartBar> expenseByCategory(String loc) {
        String sql = "SELECT COALESCE(category, 'Other') AS lbl, COALESCE(SUM(amount),0) AS amt FROM expenses "
                + locationWhere(loc, "location") + " GROUP BY COALESCE(category, 'Other') ORDER BY amt DESC LIMIT 8";
        return chartBarsAmount(sql, loc);
    }

    private List<AuditReportSnapshot.ChartBar> chartBars(String sql, Object... args) {
        List<AuditReportSnapshot.ChartBar> bars = new ArrayList<>();
        jdbcTemplate.query(sql, rs -> {
            bars.add(AuditReportSnapshot.ChartBar.builder()
                    .label(rs.getString(1))
                    .value(rs.getDouble(2))
                    .build());
            return null;
        }, args);
        return bars;
    }

    private List<AuditReportSnapshot.ChartBar> chartBarsAmount(String sql, Object... args) {
        List<AuditReportSnapshot.ChartBar> bars = new ArrayList<>();
        jdbcTemplate.query(sql, rs -> {
            bars.add(AuditReportSnapshot.ChartBar.builder()
                    .label(rs.getString(1))
                    .value(rs.getDouble(2))
                    .build());
            return null;
        }, args);
        return bars;
    }

    private String databaseVersion() {
        try {
            return jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
        } catch (Exception e) {
            return "MySQL";
        }
    }

    private String checksum(String jobId, long records, LocalDateTime at) {
        try {
            String raw = jobId + "|" + records + "|" + at.toString();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16).toUpperCase(Locale.ROOT);
        } catch (Exception e) {
            return "N/A";
        }
    }

    private static AuditReportSnapshot.KpiCard kpi(String label, String value, String accent) {
        return AuditReportSnapshot.KpiCard.builder().label(label).value(value).accent(accent).build();
    }

    private static void addInsight(List<AuditReportSnapshot.InsightCard> list, String title, String detail) {
        list.add(AuditReportSnapshot.InsightCard.builder()
                .title(title)
                .detail(detail != null && !detail.isBlank() ? detail : "—")
                .build());
    }

    private String scalarLabel(String sql, Object... args) {
        try {
            String v = jdbcTemplate.queryForObject(sql, String.class, args);
            return v != null ? v : "—";
        } catch (Exception e) {
            return "—";
        }
    }

    private String locationWhere(String loc, String column) {
        return loc.isBlank() ? " WHERE 1=1 " : " WHERE " + column + " = ? ";
    }

    private long countCustomers(String loc) {
        return loc.isBlank() ? countAll("customers") : countWhere("customers", "location = ?", loc);
    }

    private long countProducts(String loc) {
        return loc.isBlank() ? countAll("products") : countWhere("products", "location = ?", loc);
    }

    private long countProductsActive(String loc) {
        if (loc.isBlank()) {
            Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products WHERE is_active = 1", Long.class);
            return c != null ? c : 0L;
        }
        return countWhere("products", "location = ? AND is_active = 1", loc);
    }

    private long countBills(String loc) {
        return countWhere("bills_gst", loc) + countWhere("bills_non_gst", loc);
    }

    private long countWhere(String table, String whereClause, String location) {
        if (location == null || location.isBlank()) {
            Long c = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + quote(table) + " WHERE " + whereClause, Long.class);
            return c != null ? c : 0L;
        }
        Long c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + quote(table) + " WHERE " + whereClause, Long.class, location);
        return c != null ? c : 0L;
    }

    private long countWhere(String table, String loc) {
        return loc.isBlank() ? countAll(table) : countWhere(table, "location = ?", loc);
    }

    private long countAll(String table) {
        return summaryService.countAll(table);
    }

    private long cancelledBills(String loc) {
        try {
            if (loc.isBlank()) {
                Long c = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM bills_gst WHERE UPPER(bill_status) LIKE '%CANCEL%'", Long.class);
                Long c2 = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM bills_non_gst WHERE UPPER(bill_status) LIKE '%CANCEL%'", Long.class);
                return (c != null ? c : 0) + (c2 != null ? c2 : 0);
            }
            Long c = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM bills_gst WHERE location = ? AND UPPER(bill_status) LIKE '%CANCEL%'",
                    Long.class, loc);
            Long c2 = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM bills_non_gst WHERE location = ? AND UPPER(bill_status) LIKE '%CANCEL%'",
                    Long.class, loc);
            return (c != null ? c : 0) + (c2 != null ? c2 : 0);
        } catch (Exception e) {
            return countAll("bill_cancellation_logs");
        }
    }

    private long lowStockProducts(String loc) {
        try {
            String sql = "SELECT COUNT(*) FROM products WHERE min_stock IS NOT NULL AND total_sqft_stock <= min_stock"
                    + (loc.isBlank() ? "" : " AND location = ?");
            Long c = loc.isBlank()
                    ? jdbcTemplate.queryForObject(sql, Long.class)
                    : jdbcTemplate.queryForObject(sql, Long.class, loc);
            return c != null ? c : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private long outOfStock(String loc) {
        String sql = "SELECT COUNT(*) FROM products WHERE total_sqft_stock <= 0"
                + (loc.isBlank() ? "" : " AND location = ?");
        Long c = loc.isBlank()
                ? jdbcTemplate.queryForObject(sql, Long.class)
                : jdbcTemplate.queryForObject(sql, Long.class, loc);
        return c != null ? c : 0L;
    }

    private long customersWithAdvance(String loc) {
        try {
            if (loc.isBlank()) {
                Long c = jdbcTemplate.queryForObject(
                        "SELECT COUNT(DISTINCT customer_id) FROM customer_advance WHERE remaining_amount > 0", Long.class);
                return c != null ? c : 0L;
            }
            Long c = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT ca.customer_id) FROM customer_advance ca JOIN customers c ON c.id = ca.customer_id "
                            + "WHERE ca.remaining_amount > 0 AND c.location = ?", Long.class, loc);
            return c != null ? c : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private long recentStockOutCount(String loc) {
        try {
            Long c = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM inventory_transactions WHERE direction = 'OUT' AND created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)",
                    Long.class);
            return c != null ? c : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private BigDecimal totalSales(String loc) {
        return loc.isBlank()
                ? scalar("SELECT COALESCE(SUM(total_amount),0) FROM bills_gst")
                        .add(scalar("SELECT COALESCE(SUM(total_amount),0) FROM bills_non_gst"))
                : scalar("SELECT COALESCE(SUM(total_amount),0) FROM bills_gst WHERE location = ?", loc)
                        .add(scalar("SELECT COALESCE(SUM(total_amount),0) FROM bills_non_gst WHERE location = ?", loc));
    }

    private BigDecimal totalExpenses(String loc) {
        if (loc.isBlank()) {
            return scalar("SELECT COALESCE(SUM(amount), 0) FROM expenses");
        }
        return scalar("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE location = ?", loc);
    }

    private BigDecimal stockValue(String loc) {
        if (loc.isBlank()) {
            return scalar("SELECT COALESCE(SUM(total_sqft_stock * COALESCE(price_per_sqft, 0)), 0) FROM products");
        }
        return scalar("SELECT COALESCE(SUM(total_sqft_stock * COALESCE(price_per_sqft, 0)), 0) FROM products WHERE location = ?", loc);
    }

    private BigDecimal clientPurchaseTotal(String loc) {
        if (loc.isBlank()) {
            return scalar("SELECT COALESCE(SUM(total_amount), 0) FROM client_purchases");
        }
        return scalar("SELECT COALESCE(SUM(total_amount), 0) FROM client_purchases WHERE location = ?", loc);
    }

    private BigDecimal receivablesOutstanding() {
        return scalar("""
                SELECT COALESCE(SUM(CASE WHEN entry_type = 'DISBURSEMENT' THEN amount
                    WHEN entry_type = 'REPAYMENT_RECEIVED' THEN -amount ELSE 0 END), 0)
                FROM receivable_ledger_entries""");
    }

    private BigDecimal loanPayables() {
        return scalar("""
                SELECT COALESCE(SUM(CASE WHEN entry_type = 'RECEIPT' THEN amount
                    WHEN entry_type = 'REPAYMENT' THEN -amount ELSE 0 END), 0)
                FROM loan_ledger_entries""");
    }

    private BigDecimal customerAdvanceRemaining(String loc) {
        if (loc.isBlank()) {
            return scalar("SELECT COALESCE(SUM(remaining_amount), 0) FROM customer_advance");
        }
        return scalar("SELECT COALESCE(SUM(ca.remaining_amount), 0) FROM customer_advance ca "
                + "JOIN customers c ON c.id = ca.customer_id WHERE c.location = ?", loc);
    }

    private BigDecimal todaySales(String loc) {
        LocalDate today = LocalDate.now();
        return salesBetween(loc, today, today);
    }

    private BigDecimal monthSales(String loc) {
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        return salesBetween(loc, start, LocalDate.now());
    }

    private BigDecimal salesBetween(String loc, LocalDate from, LocalDate to) {
        if (loc.isBlank()) {
            return scalar("SELECT COALESCE(SUM(total_amount),0) FROM bills_gst WHERE bill_date BETWEEN ? AND ?", from, to)
                    .add(scalar("SELECT COALESCE(SUM(total_amount),0) FROM bills_non_gst WHERE bill_date BETWEEN ? AND ?", from, to));
        }
        return scalar("SELECT COALESCE(SUM(total_amount),0) FROM bills_gst WHERE location = ? AND bill_date BETWEEN ? AND ?",
                        loc, from, to)
                .add(scalar("SELECT COALESCE(SUM(total_amount),0) FROM bills_non_gst WHERE location = ? AND bill_date BETWEEN ? AND ?",
                        loc, from, to));
    }

    private BigDecimal averageBillValue(String loc) {
        long count = countBills(loc);
        if (count == 0) {
            return ZERO;
        }
        return totalSales(loc).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal scalar(String sql, Object... args) {
        BigDecimal v = jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
        return v != null ? v.setScale(2, RoundingMode.HALF_UP) : ZERO;
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
