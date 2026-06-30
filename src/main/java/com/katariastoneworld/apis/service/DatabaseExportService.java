package com.katariastoneworld.apis.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class DatabaseExportService {

    private static final int MAX_SHEET_NAME_LENGTH = 31;
    private static final int DEFAULT_COLUMN_WIDTH = 18 * 256;
    private static final int MAX_EXCEL_ROWS_PER_SHEET = 1_048_576;

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public DatabaseExportService(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Transactional(readOnly = true)
    public byte[] exportDatabaseToExcel() {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            workbook.setCompressTempFiles(true);

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dateTimeStyle = createDateTimeStyle(workbook);
            List<String> tableNames = getTableNames();
            Set<String> usedSheetNames = new HashSet<>();

            for (String tableName : tableNames) {
                exportTable(workbook, tableName, usedSheetNames, headerStyle, dateTimeStyle);
            }

            workbook.write(outputStream);
            workbook.dispose();
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to export database to Excel", e);
        }
    }

    public String buildExportFilename() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return "Kataria_Database_Export_" + timestamp + ".xlsx";
    }

    private List<String> getTableNames() throws Exception {
        List<String> tableNames = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            try (ResultSet tables = metaData.getTables(catalog, null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    if (tableName != null && !tableName.equalsIgnoreCase("flyway_schema_history")) {
                        tableNames.add(tableName);
                    }
                }
            }
        }
        tableNames.sort(Comparator.naturalOrder());
        return tableNames;
    }

    private void exportTable(SXSSFWorkbook workbook,
                             String tableName,
                             Set<String> usedSheetNames,
                             CellStyle headerStyle,
                             CellStyle dateTimeStyle) {
        jdbcTemplate.query("SELECT * FROM " + quoteIdentifier(tableName), resultSet -> {
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            int sheetNumber = 1;
            Sheet sheet = createTableSheet(workbook, tableName, sheetNumber, usedSheetNames, metaData, columnCount, headerStyle);

            int rowIndex = 1;
            while (resultSet.next()) {
                if (rowIndex >= MAX_EXCEL_ROWS_PER_SHEET) {
                    applyDefaultWidths(sheet, columnCount);
                    sheet = createTableSheet(workbook, tableName, ++sheetNumber, usedSheetNames, metaData, columnCount, headerStyle);
                    rowIndex = 1;
                }
                Row row = sheet.createRow(rowIndex++);
                for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
                    writeCell(row.createCell(columnIndex - 1), resultSet.getObject(columnIndex), dateTimeStyle);
                }
            }

            applyDefaultWidths(sheet, columnCount);
            return null;
        });
    }

    private Sheet createTableSheet(SXSSFWorkbook workbook,
                                   String tableName,
                                   int sheetNumber,
                                   Set<String> usedSheetNames,
                                   ResultSetMetaData metaData,
                                   int columnCount,
                                   CellStyle headerStyle) throws SQLException {
        String requestedName = sheetNumber == 1 ? tableName : tableName + "_" + sheetNumber;
        Sheet sheet = workbook.createSheet(uniqueSheetName(requestedName, usedSheetNames));
        writeHeader(sheet, metaData, columnCount, headerStyle);
        return sheet;
    }

    private void writeHeader(Sheet sheet, ResultSetMetaData metaData, int columnCount, CellStyle headerStyle)
            throws SQLException {
        Row header = sheet.createRow(0);
        for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
            Cell cell = header.createCell(columnIndex - 1);
            cell.setCellValue(metaData.getColumnLabel(columnIndex));
            cell.setCellStyle(headerStyle);
        }
    }

    private void writeCell(Cell cell, Object value, CellStyle dateTimeStyle) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number number) {
            if (number instanceof BigDecimal bigDecimal) {
                cell.setCellValue(bigDecimal.doubleValue());
            } else {
                cell.setCellValue(number.doubleValue());
            }
        } else if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
        } else if (value instanceof Timestamp timestamp) {
            cell.setCellValue(timestamp.toLocalDateTime());
            cell.setCellStyle(dateTimeStyle);
        } else if (value instanceof Date date) {
            cell.setCellValue(date.toLocalDate());
            cell.setCellStyle(dateTimeStyle);
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private void applyDefaultWidths(Sheet sheet, int columnCount) {
        for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
            sheet.setColumnWidth(columnIndex, DEFAULT_COLUMN_WIDTH);
        }
    }

    private CellStyle createHeaderStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle createDateTimeStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        CreationHelper creationHelper = workbook.getCreationHelper();
        style.setDataFormat(creationHelper.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
        return style;
    }

    private String uniqueSheetName(String tableName, Set<String> usedSheetNames) {
        String baseName = sanitizeSheetName(tableName);
        String sheetName = baseName;
        int suffix = 1;
        while (usedSheetNames.contains(sheetName)) {
            String suffixText = "_" + suffix++;
            int maxBaseLength = MAX_SHEET_NAME_LENGTH - suffixText.length();
            sheetName = baseName.substring(0, Math.min(baseName.length(), maxBaseLength)) + suffixText;
        }
        usedSheetNames.add(sheetName);
        return sheetName;
    }

    private String sanitizeSheetName(String value) {
        String sanitized = value.replaceAll("[\\\\/?*\\[\\]:]", "_");
        if (sanitized.isBlank()) {
            sanitized = "Sheet";
        }
        return sanitized.substring(0, Math.min(sanitized.length(), MAX_SHEET_NAME_LENGTH));
    }

    private String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }
}
