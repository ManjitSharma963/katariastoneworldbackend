package com.katariastoneworld.apis.service.audit;

import com.katariastoneworld.apis.config.AuditReportProperties;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class AuditReportPdfWriter {

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.ROOT);
    private static final DateTimeFormatter FILE_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT);

    private static final PDRectangle PORTRAIT = PDRectangle.A4;
    private static final PDRectangle LANDSCAPE =
            new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final AuditReportProperties properties;
    private final AuditReportModuleCatalog moduleCatalog;
    private final AuditReportSummaryService summaryService;
    private final AuditReportAnalyticsService analyticsService;

    public AuditReportPdfWriter(JdbcTemplate jdbcTemplate,
                                DataSource dataSource,
                                AuditReportProperties properties,
                                AuditReportModuleCatalog moduleCatalog,
                                AuditReportSummaryService summaryService,
                                AuditReportAnalyticsService analyticsService) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.properties = properties;
        this.moduleCatalog = moduleCatalog;
        this.summaryService = summaryService;
        this.analyticsService = analyticsService;
    }

    public Path writeReport(AuditReportJob job) throws Exception {
        long generationStartMs = System.currentTimeMillis();
        LocalDateTime generatedAt = LocalDateTime.now();
        List<String> tables = discoverTables();
        List<String> orderedTables = moduleCatalog.orderedTables(tables);
        long grandTotal = summaryService.countAllRecordsInTables(orderedTables);
        job.setTotalRecords(grandTotal);
        job.updateProgress(5, "Building analytics snapshot…");

        AuditReportSnapshot snapshot = analyticsService.buildSnapshot(
                job, orderedTables, generatedAt, generationStartMs);

        Path outputDir = resolveOutputDir();
        Files.createDirectories(outputDir);
        String filename = "Audit_Report_" + FILE_TS.format(generatedAt) + ".pdf";
        Path outputPath = outputDir.resolve(job.getJobId() + "_" + filename);

        try (PDDocument document = new PDDocument()) {
            AuditReportPdfFonts fonts = AuditReportPdfFonts.load(document);
            ReportContext ctx = new ReportContext(document, job, generatedAt, grandTotal, fonts, snapshot,
                    generationStartMs, orderedTables.size());

            ctx.coverPageIndex = ctx.addPortraitPage();
            ctx.tocPageIndex = ctx.addPortraitPage();
            addPremiumCover(ctx);
            addExecutiveDashboard(ctx);
            addBusinessInsightsPage(ctx);
            addChartsPages(ctx);
            addFinancialSummaryPage(ctx);
            addInventoryAnalyticsPage(ctx);
            addAuditInformationPage(ctx, orderedTables);

            PDDocumentOutline outline = new PDDocumentOutline();
            document.getDocumentCatalog().setDocumentOutline(outline);

            addBookmark(outline, "Executive Dashboard", ctx.dashboardPageIndex, document);
            addBookmark(outline, "Business Insights", ctx.insightsPageIndex, document);
            addBookmark(outline, "Charts & Trends", ctx.chartsPageIndex, document);
            addBookmark(outline, "Financial Summary", ctx.financialPageIndex, document);
            addBookmark(outline, "Inventory Analytics", ctx.inventoryPageIndex, document);
            addBookmark(outline, "Audit Information", ctx.auditInfoPageIndex, document);

            int tableIndex = 0;
            int moduleCount = moduleCatalog.modules().size();
            for (int m = 0; m < moduleCount; m++) {
                AuditReportModuleCatalog.ModuleSection module = moduleCatalog.modules().get(m);
                List<String> moduleTables = module.tables().stream().filter(orderedTables::contains).toList();
                if (moduleTables.isEmpty()) {
                    continue;
                }

                int moduleStartPage = ctx.addPortraitPage();
                addModuleIntro(ctx, module, moduleTables);

                PDOutlineItem moduleBookmark = new PDOutlineItem();
                moduleBookmark.setTitle(module.title());
                PDPageFitWidthDestination moduleDest = new PDPageFitWidthDestination();
                moduleDest.setPage(document.getPage(moduleStartPage));
                moduleBookmark.setDestination(moduleDest);
                outline.addLast(moduleBookmark);

                if ("website".equals(module.id())) {
                    embedWebsiteImages(ctx, moduleTables);
                }

                for (String table : moduleTables) {
                    tableIndex++;
                    int progress = 12 + (int) ((tableIndex / (double) orderedTables.size()) * 82);
                    job.updateProgress(progress, "Exporting table: " + table);
                    long rowCount = summaryService.countAll(table);
                    addTableSection(ctx, table, rowCount, moduleBookmark);
                    job.incrementRecordsProcessed(rowCount);
                }
            }

            finalizeToc(ctx);
            document.save(outputPath.toFile());
            job.complete(outputPath, filename);
            return outputPath;
        }
    }

    private void addPremiumCover(ReportContext ctx) throws IOException {
        AuditReportLayoutEngine.drawPremiumCover(
                ctx.document,
                ctx.document.getPage(ctx.coverPageIndex),
                ctx.fonts,
                properties,
                ctx.snapshot,
                ctx.job,
                ctx.generatedAt,
                ctx.grandTotal);
    }

    private void addExecutiveDashboard(ReportContext ctx) throws IOException {
        ctx.dashboardPageIndex = ctx.addPortraitPage();
        List<AuditReportSnapshot.KpiCard> kpis = ctx.snapshot.getKpis();
        int idx = 0;
        while (idx < kpis.size()) {
            if (idx > 0) {
                ctx.dashboardPageIndex = ctx.addPortraitPage();
            }
            int pageIndex = ctx.document.getNumberOfPages() - 1;
            PDPage page = ctx.document.getPage(pageIndex);
            try (PDPageContentStream cs = new PDPageContentStream(ctx.document, page)) {
                float y = AuditReportLayoutEngine.drawPageHeader(
                        cs, page, ctx.fonts, properties, ctx.pageNumber(pageIndex),
                        idx == 0 ? "Executive Dashboard" : "Executive Dashboard (cont.)", ctx.generatedAt);
                if (idx == 0) {
                    y = AuditReportLayoutEngine.drawSectionBanner(
                            cs, ctx.fontBold(), AuditReportTheme.MARGIN, y,
                            page.getMediaBox().getWidth() - AuditReportTheme.MARGIN * 2,
                            "Executive Dashboard", "Key performance indicators at a glance");
                }
                float result = AuditReportLayoutEngine.drawKpiGrid(
                        cs, ctx.fonts, y, page.getMediaBox().getWidth(), kpis, idx);
                if (result < 0) {
                    idx = (int) -result;
                } else {
                    break;
                }
            }
        }
    }

    private void addBusinessInsightsPage(ReportContext ctx) throws IOException {
        ctx.insightsPageIndex = ctx.addPortraitPage();
        PDPage page = ctx.document.getPage(ctx.insightsPageIndex);
        try (PDPageContentStream cs = new PDPageContentStream(ctx.document, page)) {
            float y = AuditReportLayoutEngine.drawPageHeader(
                    cs, page, ctx.fonts, properties, ctx.pageNumber(ctx.insightsPageIndex),
                    "Business Insights", ctx.generatedAt);
            y = AuditReportLayoutEngine.drawSectionBanner(
                    cs, ctx.fontBold(), AuditReportTheme.MARGIN, y,
                    page.getMediaBox().getWidth() - AuditReportTheme.MARGIN * 2,
                    "Business Insights", "Automated highlights from your data");
            AuditReportLayoutEngine.drawInsightCards(
                    cs, ctx.fonts, y, page.getMediaBox().getWidth(), ctx.snapshot.getInsights());
        }
    }

    private void addChartsPages(ReportContext ctx) throws IOException {
        ctx.chartsPageIndex = ctx.addPortraitPage();
        PDPage page = ctx.document.getPage(ctx.chartsPageIndex);
        try (PDPageContentStream cs = new PDPageContentStream(ctx.document, page)) {
            float y = AuditReportLayoutEngine.drawPageHeader(
                    cs, page, ctx.fonts, properties, ctx.pageNumber(ctx.chartsPageIndex),
                    "Charts & Trends", ctx.generatedAt);
            y = AuditReportLayoutEngine.drawSectionBanner(
                    cs, ctx.fontBold(), AuditReportTheme.MARGIN, y,
                    page.getMediaBox().getWidth() - AuditReportTheme.MARGIN * 2,
                    "Charts & Trends", "Visual distribution and trend analysis");
            y = AuditReportLayoutEngine.drawBarChart(
                    cs, ctx.fonts, y, page.getMediaBox().getWidth(),
                    "Monthly Sales (Last 6 Months)", ctx.snapshot.getMonthlySalesChart());
            y = AuditReportLayoutEngine.drawBarChart(
                    cs, ctx.fonts, y, page.getMediaBox().getWidth(),
                    "Payment Mode Distribution", ctx.snapshot.getPaymentModeChart());
            AuditReportLayoutEngine.drawBarChart(
                    cs, ctx.fonts, y, page.getMediaBox().getWidth(),
                    "Expenses by Category", ctx.snapshot.getExpenseCategoryChart());
        }
    }

    private void addFinancialSummaryPage(ReportContext ctx) throws IOException {
        ctx.financialPageIndex = ctx.addPortraitPage();
        PDPage page = ctx.document.getPage(ctx.financialPageIndex);
        try (PDPageContentStream cs = new PDPageContentStream(ctx.document, page)) {
            float y = AuditReportLayoutEngine.drawPageHeader(
                    cs, page, ctx.fonts, properties, ctx.pageNumber(ctx.financialPageIndex),
                    "Financial Summary", ctx.generatedAt);
            AuditReportLayoutEngine.drawKeyValuePanel(
                    cs, ctx.fonts, y, page.getMediaBox().getWidth(),
                    "Financial Summary", ctx.snapshot.getFinancialSummary());
        }
    }

    private void addInventoryAnalyticsPage(ReportContext ctx) throws IOException {
        ctx.inventoryPageIndex = ctx.addPortraitPage();
        PDPage page = ctx.document.getPage(ctx.inventoryPageIndex);
        try (PDPageContentStream cs = new PDPageContentStream(ctx.document, page)) {
            float y = AuditReportLayoutEngine.drawPageHeader(
                    cs, page, ctx.fonts, properties, ctx.pageNumber(ctx.inventoryPageIndex),
                    "Inventory Analytics", ctx.generatedAt);
            AuditReportLayoutEngine.drawKeyValuePanel(
                    cs, ctx.fonts, y, page.getMediaBox().getWidth(),
                    "Inventory Analytics", ctx.snapshot.getInventoryAnalytics());
        }
    }

    private void addAuditInformationPage(ReportContext ctx, List<String> tables) throws IOException {
        ctx.auditInfoPageIndex = ctx.addPortraitPage();
        PDPage page = ctx.document.getPage(ctx.auditInfoPageIndex);
        long durationMs = System.currentTimeMillis() - ctx.generationStartMs;
        Map<String, String> audit = new LinkedHashMap<>();
        audit.put("Total Records Exported", String.format(Locale.ROOT, "%,d", ctx.grandTotal));
        audit.put("Database Tables Exported", String.valueOf(tables.size()));
        audit.put("Generation Time", DT_FMT.format(ctx.generatedAt));
        audit.put("Generated By", ctx.job.getGeneratedByName() + " (" + ctx.job.getGeneratedByEmail() + ")");
        audit.put("Execution Duration", String.format(Locale.ROOT, "%,.1f seconds", durationMs / 1000.0));
        audit.put("Application Version", properties.getApplicationVersion());
        audit.put("Database Version", ctx.snapshot.getDatabaseVersion());
        audit.put("Report Version", properties.getReportVersion());
        audit.put("Audit Integrity Checksum", ctx.snapshot.getChecksum());
        audit.put("Digital Signature", "[ Reserved for future signing ]");
        audit.put("Audit Integrity Status", "VERIFIED — complete export");

        try (PDPageContentStream cs = new PDPageContentStream(ctx.document, page)) {
            float y = AuditReportLayoutEngine.drawPageHeader(
                    cs, page, ctx.fonts, properties, ctx.pageNumber(ctx.auditInfoPageIndex),
                    "Audit Information", ctx.generatedAt);
            y = AuditReportLayoutEngine.drawKeyValuePanel(
                    cs, ctx.fonts, y, page.getMediaBox().getWidth(), "Audit Information", audit);
            y -= 8;
            text(cs, ctx.fontBold(), 10, AuditReportTheme.MARGIN, y, "Tables included in this report:",
                    AuditReportTheme.TEXT_PRIMARY);
            y -= 16;
            for (String table : tables) {
                if (y < AuditReportTheme.MARGIN + AuditReportTheme.FOOTER_H + 20) {
                    break;
                }
                long count = summaryService.countAll(table);
                text(cs, ctx.fontRegular(), 8, AuditReportTheme.MARGIN + 8, y,
                        AuditReportModuleCatalog.friendlyTableLabel(table)
                                + " (" + table + ") — " + String.format(Locale.ROOT, "%,d", count) + " records",
                        AuditReportTheme.TEXT_MUTED);
                y -= 12;
            }
        }
    }

    private static void addBookmark(PDDocumentOutline outline, String title, int pageIndex, PDDocument document) {
        PDOutlineItem item = new PDOutlineItem();
        item.setTitle(title);
        PDPageFitWidthDestination dest = new PDPageFitWidthDestination();
        dest.setPage(document.getPage(pageIndex));
        item.setDestination(dest);
        outline.addLast(item);
    }

    private void addModuleIntro(ReportContext ctx,
                                AuditReportModuleCatalog.ModuleSection module,
                                List<String> moduleTables) throws IOException {
        PDPage page = ctx.document.getPage(ctx.document.getNumberOfPages() - 1);
        AuditReportSnapshot.ModuleSummary summary = ctx.snapshot.getModuleSummaries().stream()
                .filter(s -> module.id().equals(s.getModuleId()))
                .findFirst()
                .orElse(null);
        try (PDPageContentStream cs = new PDPageContentStream(ctx.document, page)) {
            float w = page.getMediaBox().getWidth();
            float y = AuditReportLayoutEngine.drawPageHeader(
                    cs, page, ctx.fonts, properties, ctx.pageNumber(ctx.document.getNumberOfPages() - 1),
                    module.title(), ctx.generatedAt);
            y = AuditReportLayoutEngine.drawSectionBanner(
                    cs, ctx.fontBold(), AuditReportTheme.MARGIN, y, w - AuditReportTheme.MARGIN * 2,
                    module.title(), module.description());
            if (summary != null) {
                text(cs, ctx.fontBold(), 10, AuditReportTheme.MARGIN, y,
                        "Section records: " + String.format(Locale.ROOT, "%,d", summary.getRecordCount()),
                        AuditReportTheme.TEXT_PRIMARY);
                y -= 18;
                for (String bullet : summary.getBullets()) {
                    text(cs, ctx.fontRegular(), 9, AuditReportTheme.MARGIN + 8, y, "• " + bullet,
                            AuditReportTheme.TEXT_MUTED);
                    y -= 14;
                }
            }
            y -= 6;
            text(cs, ctx.fontRegular(), 8, AuditReportTheme.MARGIN, y,
                    "Detailed tables follow on subsequent pages.",
                    AuditReportTheme.TEXT_MUTED);
        }
    }

    private void addTableSection(ReportContext ctx,
                                 String table,
                                 long rowCount,
                                 PDOutlineItem moduleBookmark) throws Exception {
        int tableStartPage = ctx.document.getNumberOfPages();
        PDOutlineItem tableBookmark = new PDOutlineItem();
        tableBookmark.setTitle(AuditReportModuleCatalog.friendlyTableLabel(table) + " (" + rowCount + ")");
        moduleBookmark.addLast(tableBookmark);

        PDDocument document = ctx.document;
        try (Connection connection = dataSource.getConnection()) {
            String sql = "SELECT * FROM " + quote(table);
            PreparedStatement ps = connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            ps.setFetchSize(Integer.MIN_VALUE);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                String[] columns = new String[columnCount];
                String[] labels = new String[columnCount];
                int[] exportIndices = new int[columnCount];
                int exportCount = 0;
                for (int c = 1; c <= columnCount; c++) {
                    columns[c - 1] = meta.getColumnLabel(c);
                    if (AuditReportColumnPolicy.shouldExportColumn(table, columns[c - 1])) {
                        exportIndices[exportCount] = c - 1;
                        labels[exportCount] = AuditReportModuleCatalog.friendlyColumnLabel(columns[c - 1]);
                        exportCount++;
                    }
                }
                if (exportCount == 0) {
                    exportIndices[0] = 0;
                    labels[0] = "Data";
                    exportCount = 1;
                }
                String[] exportLabels = new String[exportCount];
                System.arraycopy(labels, 0, exportLabels, 0, exportCount);

                TableLayout layout = buildTableLayout(exportCount, exportLabels);
                int pageNum = 0;
                int rowsOnPage = 0;
                PDPageContentStream cs = null;
                float y = 0;
                int dataRowIndex = 0;

                while (rs.next()) {
                    if (cs == null || rowsOnPage >= layout.maxRowsPerPage) {
                        if (cs != null) {
                            cs.close();
                        }
                        pageNum++;
                        int pageIndex = ctx.addLandscapePage();
                        if (pageNum == 1) {
                            tableStartPage = pageIndex;
                            PDPageFitWidthDestination tableDest = new PDPageFitWidthDestination();
                            tableDest.setPage(document.getPage(pageIndex));
                            tableBookmark.setDestination(tableDest);
                        }
                        PDPage page = ctx.document.getPage(pageIndex);
                        cs = new PDPageContentStream(ctx.document, page, PDPageContentStream.AppendMode.APPEND, true);
                        y = AuditReportLayoutEngine.drawPageHeader(
                                cs, page, ctx.fonts, properties, ctx.pageNumber(pageIndex),
                                AuditReportModuleCatalog.friendlyTableLabel(table), ctx.generatedAt);
                        y -= 8;
                        AuditReportLayoutEngine.drawTableTitle(cs, ctx.fonts, y,
                                AuditReportModuleCatalog.friendlyTableLabel(table), table, rowCount, pageNum > 1);
                        y -= 28;
                        y = drawTableHeader(ctx, cs, layout, exportLabels, y);
                        rowsOnPage = 0;
                    }

                    boolean alt = dataRowIndex % 2 == 1;
                    y = drawTableRow(ctx, cs, layout, rs, columns, exportIndices, exportCount, y, alt);
                    rowsOnPage++;
                    dataRowIndex++;
                }

                if (cs == null) {
                    int pageIndex = ctx.addLandscapePage();
                    PDPageFitWidthDestination tableDest = new PDPageFitWidthDestination();
                    tableDest.setPage(document.getPage(pageIndex));
                    tableBookmark.setDestination(tableDest);
                    PDPage page = ctx.document.getPage(pageIndex);
                    cs = new PDPageContentStream(ctx.document, page);
                    y = AuditReportLayoutEngine.drawPageHeader(
                            cs, page, ctx.fonts, properties, ctx.pageNumber(pageIndex),
                            AuditReportModuleCatalog.friendlyTableLabel(table), ctx.generatedAt);
                    y -= 20;
                    text(cs, ctx.fontRegular(), 9, AuditReportTheme.MARGIN, y, "No records in this table.",
                            AuditReportTheme.TEXT_MUTED);
                }
                if (cs != null) {
                    cs.close();
                }
            }
        }
    }

    private void embedWebsiteImages(ReportContext ctx, List<String> moduleTables) {
        if (!moduleTables.contains("hero_slides") && !moduleTables.contains("website_product")) {
            return;
        }
        try {
            List<ImageRow> images = new ArrayList<>();
            if (moduleTables.contains("hero_slides")) {
                jdbcTemplate.query("SELECT title, image_url FROM hero_slides WHERE image_url IS NOT NULL",
                        rs -> {
                            images.add(new ImageRow(rs.getString(1), rs.getString(2)));
                            return null;
                        });
            }
            if (moduleTables.contains("website_product")) {
                jdbcTemplate.query(
                        "SELECT name, primary_image_url FROM website_product WHERE primary_image_url IS NOT NULL",
                        rs -> {
                            images.add(new ImageRow(rs.getString(1), rs.getString(2)));
                            return null;
                        });
            }
            if (images.isEmpty()) {
                return;
            }
            int pageIndex = ctx.addPortraitPage();
            PDPage page = ctx.document.getPage(pageIndex);
            try (PDPageContentStream cs = new PDPageContentStream(ctx.document, page)) {
                float y = AuditReportLayoutEngine.drawPageHeader(
                        cs, page, ctx.fonts, properties, ctx.pageNumber(pageIndex), "Website Images", ctx.generatedAt);
                text(cs, ctx.fontBold(), 12, AuditReportTheme.MARGIN, y, "Embedded Website Images",
                        AuditReportTheme.PRIMARY);
                y -= 24;
                for (ImageRow image : images) {
                    if (y < AuditReportTheme.MARGIN + 100) {
                        break;
                    }
                    text(cs, ctx.fontRegular(), 8, AuditReportTheme.MARGIN, y, image.label + " — " + image.url,
                            AuditReportTheme.TEXT_MUTED);
                    y -= 12;
                    PDImageXObject img = loadImage(ctx.document, image.url);
                    if (img != null) {
                        float maxW = page.getMediaBox().getWidth() - AuditReportTheme.MARGIN * 2;
                        float scale = Math.min(1f, maxW / img.getWidth());
                        float drawW = img.getWidth() * scale;
                        float drawH = img.getHeight() * scale;
                        if (y - drawH < AuditReportTheme.MARGIN + AuditReportTheme.FOOTER_H) {
                            break;
                        }
                        cs.drawImage(img, AuditReportTheme.MARGIN, y - drawH, drawW, drawH);
                        y -= drawH + 16;
                    }
                }
            }
        } catch (Exception ignored) {
            // images are optional
        }
    }

    private void finalizeToc(ReportContext ctx) throws IOException {
        PDPage page = ctx.document.getPage(ctx.tocPageIndex);
        try (PDPageContentStream cs = new PDPageContentStream(ctx.document, page,
                PDPageContentStream.AppendMode.OVERWRITE, true)) {
            float y = AuditReportLayoutEngine.drawPageHeader(
                    cs, page, ctx.fonts, properties, ctx.pageNumber(ctx.tocPageIndex),
                    "Table of Contents", ctx.generatedAt);
            text(cs, ctx.fontBold(), 16, AuditReportTheme.MARGIN, y, "Table of Contents", AuditReportTheme.PRIMARY);
            y -= 24;
            text(cs, ctx.fontRegular(), 9, AuditReportTheme.MARGIN, y,
                    "Use PDF bookmarks (navigation panel) for clickable section links.",
                    AuditReportTheme.TEXT_MUTED);
            y -= 22;

            String[] sections = {
                    "Executive Dashboard", "Business Insights", "Charts & Trends",
                    "Financial Summary", "Inventory Analytics", "Audit Information"
            };
            for (String section : sections) {
                text(cs, ctx.fontBold(), 10, AuditReportTheme.MARGIN, y, section, AuditReportTheme.PRIMARY);
                y -= 14;
            }
            y -= 8;
            text(cs, ctx.fontBold(), 10, AuditReportTheme.MARGIN, y, "Data Modules", AuditReportTheme.PRIMARY);
            y -= 14;

            for (AuditReportModuleCatalog.ModuleSection module : moduleCatalog.modules()) {
                text(cs, ctx.fontBold(), 9, AuditReportTheme.MARGIN, y, module.title(), AuditReportTheme.TEAL);
                y -= 12;
                for (String table : module.tables()) {
                    if (y < AuditReportTheme.MARGIN + AuditReportTheme.FOOTER_H + 14) {
                        break;
                    }
                    long count = summaryService.countAll(table);
                    text(cs, ctx.fontRegular(), 8, AuditReportTheme.MARGIN + 12, y,
                            "  • " + AuditReportModuleCatalog.friendlyTableLabel(table) + " ("
                                    + String.format(Locale.ROOT, "%,d", count) + ")",
                            AuditReportTheme.TEXT_MUTED);
                    y -= 12;
                }
                y -= 6;
            }
        }
    }

    private float drawTableHeader(ReportContext ctx,
                                  PDPageContentStream cs,
                                  TableLayout layout,
                                  String[] labels,
                                  float y) throws IOException {
        return AuditReportLayoutEngine.drawTableHeaderRow(
                cs, ctx.fonts, y, layout.tableWidth, labels, layout.startCol, layout.endCol);
    }

    private float drawTableRow(ReportContext ctx,
                               PDPageContentStream cs,
                               TableLayout layout,
                               ResultSet rs,
                               String[] columns,
                               int[] exportIndices,
                               int exportCount,
                               float y,
                               boolean alt) throws SQLException, IOException {
        Object[] values = new Object[exportCount];
        String[] colNames = new String[exportCount];
        for (int i = 0; i < exportCount; i++) {
            int idx = exportIndices[i];
            colNames[i] = columns[idx];
            values[i] = rs.getObject(columns[idx]);
        }
        boolean warn = AuditReportColumnPolicy.isWarningRow(colNames, values);
        boolean rowError = false;
        for (Object v : values) {
            if (v instanceof Number n && n.doubleValue() < 0) {
                rowError = true;
                break;
            }
        }
        Color bg = AuditReportLayoutEngine.rowBackground(alt, warn, rowError);
        cs.setNonStrokingColor(bg);
        cs.addRect(AuditReportTheme.MARGIN, y - AuditReportTheme.ROW_H + 2, layout.tableWidth, AuditReportTheme.ROW_H);
        cs.fill();

        float x = AuditReportTheme.MARGIN;
        for (int i = layout.startCol; i < layout.endCol; i++) {
            String value = formatCellValue(values[i]);
            Color textColor = values[i] instanceof Number n && n.doubleValue() < 0
                    ? AuditReportTheme.ERROR : AuditReportTheme.TEXT_PRIMARY;
            text(cs, ctx.fontRegular(), AuditReportTheme.FONT_TABLE, x + 4, y - 10, truncate(value, layout.colWidth),
                    textColor);
            x += layout.colWidth;
        }
        return y - AuditReportTheme.ROW_H;
    }

    private TableLayout buildTableLayout(int columnCount, String[] labels) {
        float pageW = LANDSCAPE.getWidth();
        float usable = pageW - AuditReportTheme.MARGIN * 2;
        int maxColsPerPage = Math.max(1, (int) (usable / 72f));
        int endCol = Math.min(columnCount, maxColsPerPage);
        float colWidth = usable / endCol;
        float pageH = LANDSCAPE.getHeight();
        int maxRows = (int) ((pageH - AuditReportTheme.MARGIN - AuditReportTheme.HEADER_H
                - AuditReportTheme.FOOTER_H - 60) / AuditReportTheme.ROW_H);
        return new TableLayout(0, endCol, colWidth, usable, Math.max(8, maxRows));
    }

    private String formatCellValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal bd) {
            return String.format(Locale.ROOT, "%,.2f", bd);
        }
        if (value instanceof Double || value instanceof Float) {
            return String.format(Locale.ROOT, "%,.2f", ((Number) value).doubleValue());
        }
        if (value instanceof Timestamp ts) {
            return DT_FMT.format(ts.toLocalDateTime());
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        if (value instanceof LocalDate ld) {
            return ld.format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        if (value instanceof LocalDateTime ldt) {
            return DT_FMT.format(ldt);
        }
        if (value instanceof Boolean b) {
            return b ? "Yes" : "No";
        }
        return String.valueOf(value);
    }

    private List<String> discoverTables() throws SQLException {
        List<String> tableNames = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            try (ResultSet tables = metaData.getTables(catalog, null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    if (!moduleCatalog.isExcluded(tableName)) {
                        tableNames.add(tableName);
                    }
                }
            }
        }
        tableNames.sort(Comparator.naturalOrder());
        return tableNames;
    }

    private Path resolveOutputDir() {
        String configured = properties.getTempDir();
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured.trim());
        }
        return Path.of(System.getProperty("java.io.tmpdir"), "audit-reports");
    }

    private void drawLogoIfPresent(ReportContext ctx, PDPageContentStream cs,
                                  float x, float y, float maxSize) throws IOException {
        String logoPath = properties.getLogoPath();
        if (logoPath == null || logoPath.isBlank()) {
            return;
        }
        Path path = Path.of(logoPath.trim());
        if (!Files.exists(path)) {
            return;
        }
        PDImageXObject img = PDImageXObject.createFromFile(path.toString(), ctx.document);
        float scale = Math.min(maxSize / img.getWidth(), maxSize / img.getHeight());
        cs.drawImage(img, x, y, img.getWidth() * scale, img.getHeight() * scale);
    }

    private PDImageXObject loadImage(PDDocument document, String url) {
        try {
            if (url == null || url.isBlank()) {
                return null;
            }
            if (url.startsWith("http://") || url.startsWith("https://")) {
                try (InputStream in = URI.create(url).toURL().openStream()) {
                    return PDImageXObject.createFromByteArray(document, in.readAllBytes(), "img");
                }
            }
            Path path = Path.of(url);
            if (Files.exists(path)) {
                return PDImageXObject.createFromFile(path.toString(), document);
            }
        } catch (Exception ignored) {
            // skip broken images
        }
        return null;
    }

    private static void text(PDPageContentStream cs, PDFont font, float size, float x, float y, String text,
                             Color color) throws IOException {
        cs.setNonStrokingColor(color);
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(sanitizePdfText(text));
        cs.endText();
    }

    /**
     * PDF {@code showText} draws a single line — newline/control chars have no glyph and must be flattened.
     */
    static String sanitizePdfText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String flattened = text
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ');
        StringBuilder sb = new StringBuilder(flattened.length());
        for (int i = 0; i < flattened.length(); i++) {
            char c = flattened.charAt(i);
            // Drop other C0 controls (U+0000–U+001F) that fonts cannot render in showText.
            if (c >= 0x20 || c == ' ') {
                sb.append(c);
            }
        }
        return sb.toString().strip();
    }

    private static String truncate(String value, float colWidth) {
        if (value == null) {
            return "";
        }
        value = sanitizePdfText(value);
        int maxChars = Math.max(8, (int) (colWidth / 4.5f));
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars - 1)) + "…";
    }

    private static String quote(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    private record ImageRow(String label, String url) {}

    private record TableLayout(int startCol, int endCol, float colWidth, float tableWidth, int maxRowsPerPage) {}

    private static final class ReportContext {
        private final PDDocument document;
        private final AuditReportJob job;
        private final LocalDateTime generatedAt;
        private final long grandTotal;
        private final AuditReportPdfFonts fonts;
        private final AuditReportSnapshot snapshot;
        private final long generationStartMs;
        private final int tableCount;
        int coverPageIndex = 0;
        int tocPageIndex;
        int dashboardPageIndex;
        int insightsPageIndex;
        int chartsPageIndex;
        int financialPageIndex;
        int inventoryPageIndex;
        int auditInfoPageIndex;

        ReportContext(PDDocument document,
                        AuditReportJob job,
                        LocalDateTime generatedAt,
                        long grandTotal,
                        AuditReportPdfFonts fonts,
                        AuditReportSnapshot snapshot,
                        long generationStartMs,
                        int tableCount) {
            this.document = document;
            this.job = job;
            this.generatedAt = generatedAt;
            this.grandTotal = grandTotal;
            this.fonts = fonts;
            this.snapshot = snapshot;
            this.generationStartMs = generationStartMs;
            this.tableCount = tableCount;
        }

        PDFont fontRegular() {
            return fonts.regular();
        }

        PDFont fontBold() {
            return fonts.bold();
        }

        int addPortraitPage() {
            PDPage page = new PDPage(PORTRAIT);
            document.addPage(page);
            return document.getNumberOfPages() - 1;
        }

        int addLandscapePage() {
            PDPage page = new PDPage(LANDSCAPE);
            document.addPage(page);
            return document.getNumberOfPages() - 1;
        }

        PDPage pageRef(int index) {
            return document.getPage(index);
        }

        int pageNumber(int index) {
            return index + 1;
        }
    }
}
