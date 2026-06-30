package com.katariastoneworld.apis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "audit.report")
public class AuditReportProperties {

    private String applicationName = "Kataria Stone World Inventory";
    private String companyName = "Kataria Stone World";
    private String reportVersion = "1.0";
    private String applicationVersion = "1.0.0";
    /** Optional filesystem path to a PNG/JPG company logo. */
    private String logoPath = "";
    /** JDBC fetch size for streaming table rows. */
    private int fetchSize = 500;
    /** Temp directory for generated PDF files (defaults to java.io.tmpdir/audit-reports). */
    private String tempDir = "";
    /** Max characters shown per table cell. */
    private int maxCellChars = 120;
}
