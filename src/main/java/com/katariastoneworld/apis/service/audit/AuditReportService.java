package com.katariastoneworld.apis.service.audit;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AuditReportService {

    private final AuditReportPdfWriter pdfWriter;

    public AuditReportService(AuditReportPdfWriter pdfWriter) {
        this.pdfWriter = pdfWriter;
    }

    @Async("auditReportExecutor")
    public void generateAsync(AuditReportJob job) {
        job.markRunning();
        job.updateProgress(2, "Preparing audit report…");
        try {
            pdfWriter.writeReport(job);
        } catch (Exception ex) {
            job.fail(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        }
    }
}
