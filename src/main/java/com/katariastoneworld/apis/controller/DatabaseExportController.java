package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.service.DatabaseExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/export", "/export"})
@Tag(name = "Database Export", description = "Admin-only database export endpoints")
public class DatabaseExportController {

    private static final MediaType EXCEL_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final DatabaseExportService databaseExportService;

    public DatabaseExportController(DatabaseExportService databaseExportService) {
        this.databaseExportService = databaseExportService;
    }

    @Operation(
            summary = "Download full database export",
            description = "Exports all application tables into one Excel workbook with one sheet per table.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping(value = "/database", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @RequiresRole("admin")
    public ResponseEntity<byte[]> downloadDatabaseExport() {
        byte[] excelBytes = databaseExportService.exportDatabaseToExcel();
        String filename = databaseExportService.buildExportFilename();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(EXCEL_MEDIA_TYPE);
        headers.setContentLength(excelBytes.length);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(excelBytes);
    }
}
