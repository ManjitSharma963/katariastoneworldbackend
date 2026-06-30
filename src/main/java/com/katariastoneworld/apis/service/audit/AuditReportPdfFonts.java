package com.katariastoneworld.apis.service.audit;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.IOException;
import java.io.InputStream;

/**
 * Unicode-capable fonts for audit PDFs (supports ₹ and other non-WinAnsi characters).
 */
public final class AuditReportPdfFonts {

    private final PDFont regular;
    private final PDFont bold;

    private AuditReportPdfFonts(PDFont regular, PDFont bold) {
        this.regular = regular;
        this.bold = bold;
    }

    public PDFont regular() {
        return regular;
    }

    public PDFont bold() {
        return bold;
    }

    public static AuditReportPdfFonts load(PDDocument document) throws IOException {
        try (InputStream regularStream = openFont("/fonts/DejaVuSans.ttf");
             InputStream boldStream = openFont("/fonts/DejaVuSans-Bold.ttf")) {
            if (regularStream == null || boldStream == null) {
                throw new IOException("DejaVu font files missing from classpath (/fonts/)");
            }
            return new AuditReportPdfFonts(
                    PDType0Font.load(document, regularStream),
                    PDType0Font.load(document, boldStream));
        }
    }

    private static InputStream openFont(String classpathResource) {
        return AuditReportPdfFonts.class.getResourceAsStream(classpathResource);
    }
}
