package com.katariastoneworld.apis.service.audit;

import java.util.Locale;
import java.util.Set;

/** Column visibility and row highlighting rules for audit tables. */
public final class AuditReportColumnPolicy {

    private static final Set<String> HIDDEN_COLUMNS = Set.of(
            "password"
    );

    private AuditReportColumnPolicy() {}

    public static boolean shouldExportColumn(String table, String column) {
        if (column == null) {
            return false;
        }
        return !HIDDEN_COLUMNS.contains(column.toLowerCase(Locale.ROOT));
    }

    public static boolean isWarningRow(String[] columns, Object[] values) {
        for (int i = 0; i < columns.length && i < values.length; i++) {
            String col = columns[i].toLowerCase(Locale.ROOT);
            String val = String.valueOf(values[i] != null ? values[i] : "").toUpperCase(Locale.ROOT);
            if (col.contains("status") && val.contains("CANCEL")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isNegativeHighlight(String column, Object value) {
        return value instanceof Number n && n.doubleValue() < 0;
    }
}
