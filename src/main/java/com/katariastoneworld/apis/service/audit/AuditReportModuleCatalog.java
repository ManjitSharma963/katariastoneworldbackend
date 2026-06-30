package com.katariastoneworld.apis.service.audit;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class AuditReportModuleCatalog {

    public record ModuleSection(String id, String title, String description, List<String> tables) {}

    private static final Set<String> EXCLUDED_TABLES = Set.of(
            "flyway_schema_history"
    );

    private static final List<ModuleSection> MODULES = List.of(
            new ModuleSection("sales", "Sales & Billing",
                    "GST and non-GST bills, line items, payments, events, versions, returns, and cancellations.",
                    List.of("bills_gst", "bills_non_gst", "bill_items_gst", "bill_items_non_gst",
                            "bill_payments", "bill_events", "bill_versions", "bill_cancellation_logs",
                            "bill_inventory_returns", "bill_inventory_return_lines")),
            new ModuleSection("customers", "Customers & Advances",
                    "Customer master data, advance balances, usage, and wallet transactions.",
                    List.of("customers", "customer_advance", "customer_advance_usage", "customer_wallet_transactions")),
            new ModuleSection("inventory", "Inventory & Products",
                    "Product catalog, stock movements, reservations, and change history.",
                    List.of("products", "inventory_transactions", "inventory_history",
                            "inventory_reservations", "product_change_history")),
            new ModuleSection("suppliers", "Suppliers, Dealers & Client Accounts",
                    "Supplier and dealer masters, client purchases, payments, and account settings.",
                    List.of("suppliers", "dealers", "client_purchases", "client_purchase_payments",
                            "client_supplier_accounts", "client_transactions")),
            new ModuleSection("expenses", "Expenses & Payroll",
                    "Operating expenses, employees, and payroll ledger entries.",
                    List.of("expenses", "employees", "employee_payroll_ledger")),
            new ModuleSection("ledger", "Financial Ledger",
                    "Unified money transactions (cash, UPI, bank movements).",
                    List.of("transactions")),
            new ModuleSection("loans", "Loans & Receivables",
                    "Lenders, borrowers, loan ledger, and receivable ledger entries.",
                    List.of("loan_lenders", "loan_borrowers", "loan_ledger_entries", "receivable_ledger_entries")),
            new ModuleSection("budget", "Budget & Daily Closing",
                    "Daily closing snapshots and budget reconciliation data.",
                    List.of("daily_closing_snapshot")),
            new ModuleSection("website", "Website & Catalog",
                    "Public website categories, products, and hero carousel slides.",
                    List.of("categories", "website_product", "hero_slides")),
            new ModuleSection("admin", "Administration & Configuration",
                    "Users, seller profiles, and GST master configuration.",
                    List.of("users", "sellers", "state_gst_master"))
    );

    public List<ModuleSection> modules() {
        return MODULES;
    }

    public Map<String, String> tableToModuleTitle() {
        Map<String, String> map = new LinkedHashMap<>();
        for (ModuleSection module : MODULES) {
            for (String table : module.tables()) {
                map.put(table.toLowerCase(Locale.ROOT), module.title());
            }
        }
        return map;
    }

    public List<String> orderedTables(List<String> discoveredTables) {
        List<String> ordered = new ArrayList<>();
        for (ModuleSection module : MODULES) {
            for (String table : module.tables()) {
                if (discoveredTables.contains(table)) {
                    ordered.add(table);
                }
            }
        }
        for (String table : discoveredTables) {
            if (!ordered.contains(table)) {
                ordered.add(table);
            }
        }
        return ordered;
    }

    public boolean isExcluded(String tableName) {
        return tableName == null || EXCLUDED_TABLES.contains(tableName.toLowerCase(Locale.ROOT));
    }

    public static String friendlyColumnLabel(String columnName) {
        if (columnName == null || columnName.isBlank()) {
            return "";
        }
        String[] parts = columnName.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    public static String friendlyTableLabel(String tableName) {
        return friendlyColumnLabel(tableName);
    }
}
