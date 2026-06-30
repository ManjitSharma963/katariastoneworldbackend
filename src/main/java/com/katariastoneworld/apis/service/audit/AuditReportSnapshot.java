package com.katariastoneworld.apis.service.audit;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class AuditReportSnapshot {

    @Data
    @Builder
    public static class KpiCard {
        private String label;
        private String value;
        private String accent; // teal, gold, success, warning, primary
    }

    @Data
    @Builder
    public static class InsightCard {
        private String title;
        private String detail;
    }

    @Data
    @Builder
    public static class ChartBar {
        private String label;
        private double value;
    }

    @Data
    @Builder
    public static class ModuleSummary {
        private String moduleId;
        private String title;
        private long recordCount;
        private List<String> bullets;
    }

    private String reportId;
    private String databaseVersion;
    private String branch;
    private String reportPeriod;
    private String checksum;

    @Builder.Default
    private List<KpiCard> kpis = new ArrayList<>();

    @Builder.Default
    private List<InsightCard> insights = new ArrayList<>();

    @Builder.Default
    private Map<String, String> financialSummary = new LinkedHashMap<>();

    @Builder.Default
    private Map<String, String> inventoryAnalytics = new LinkedHashMap<>();

    @Builder.Default
    private List<ChartBar> paymentModeChart = new ArrayList<>();

    @Builder.Default
    private List<ChartBar> monthlySalesChart = new ArrayList<>();

    @Builder.Default
    private List<ChartBar> expenseCategoryChart = new ArrayList<>();

    @Builder.Default
    private List<ModuleSummary> moduleSummaries = new ArrayList<>();
}
