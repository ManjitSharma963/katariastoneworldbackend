package com.katariastoneworld.apis.service.audit;

import lombok.Getter;

import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Getter
public class AuditReportJob {

    public enum Status {
        QUEUED, RUNNING, COMPLETED, FAILED
    }

    private final String jobId;
    private final Long userId;
    private final String location;
    private final String generatedByName;
    private final String generatedByEmail;
    private final Instant startedAt;
    private final AtomicReference<Status> status = new AtomicReference<>(Status.QUEUED);
    private final AtomicInteger progressPercent = new AtomicInteger(0);
    private final AtomicReference<String> message = new AtomicReference<>("Queued");
    private final AtomicLong recordsProcessed = new AtomicLong(0);
    private final AtomicLong totalRecords = new AtomicLong(0);
    private final AtomicReference<String> filename = new AtomicReference<>("");
    private final AtomicReference<String> errorMessage = new AtomicReference<>("");
    private volatile Path outputPath;

    public AuditReportJob(String jobId,
                          Long userId,
                          String location,
                          String generatedByName,
                          String generatedByEmail) {
        this.jobId = jobId;
        this.userId = userId;
        this.location = location;
        this.generatedByName = generatedByName;
        this.generatedByEmail = generatedByEmail;
        this.startedAt = Instant.now();
    }

    public void markRunning() {
        status.set(Status.RUNNING);
        message.set("Starting audit report generation…");
    }

    public void updateProgress(int percent, String msg) {
        progressPercent.set(Math.max(0, Math.min(100, percent)));
        if (msg != null && !msg.isBlank()) {
            message.set(msg);
        }
    }

    public void incrementRecordsProcessed(long delta) {
        recordsProcessed.addAndGet(delta);
    }

    public void setTotalRecords(long total) {
        totalRecords.set(total);
    }

    public void complete(Path path, String file) {
        outputPath = path;
        filename.set(file);
        status.set(Status.COMPLETED);
        progressPercent.set(100);
        message.set("Audit report ready for download.");
    }

    public void fail(String error) {
        errorMessage.set(error != null ? error : "Unknown error");
        status.set(Status.FAILED);
        message.set("Audit report generation failed.");
    }
}
