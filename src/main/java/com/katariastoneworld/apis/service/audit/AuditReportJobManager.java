package com.katariastoneworld.apis.service.audit;

import com.katariastoneworld.apis.dto.AuditReportStatusDTO;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuditReportJobManager {

    private static final Duration JOB_TTL = Duration.ofHours(6);

    private final Map<String, AuditReportJob> jobs = new ConcurrentHashMap<>();
    private final AuditReportService auditReportService;

    public AuditReportJobManager(@Lazy AuditReportService auditReportService) {
        this.auditReportService = auditReportService;
    }

    public String startJob(Long userId, String location, String generatedByName, String generatedByEmail) {
        purgeExpiredJobs();
        String jobId = UUID.randomUUID().toString();
        AuditReportJob job = new AuditReportJob(jobId, userId, location, generatedByName, generatedByEmail);
        jobs.put(jobId, job);
        auditReportService.generateAsync(job);
        return jobId;
    }

    public AuditReportJob getJob(String jobId) {
        return jobs.get(jobId);
    }

    public AuditReportStatusDTO toStatusDto(AuditReportJob job) {
        if (job == null) {
            return null;
        }
        return AuditReportStatusDTO.builder()
                .jobId(job.getJobId())
                .status(job.getStatus().get().name())
                .progressPercent(job.getProgressPercent().get())
                .message(job.getMessage().get())
                .recordsProcessed(job.getRecordsProcessed().get())
                .totalRecords(job.getTotalRecords().get())
                .filename(job.getFilename().get())
                .error(job.getErrorMessage().get())
                .build();
    }

    public void purgeExpiredJobs() {
        Instant cutoff = Instant.now().minus(JOB_TTL);
        jobs.entrySet().removeIf(entry -> {
            AuditReportJob job = entry.getValue();
            if (job.getStartedAt().isBefore(cutoff)) {
                deleteOutput(job);
                return true;
            }
            return false;
        });
    }

    public void deleteOutput(AuditReportJob job) {
        if (job == null || job.getOutputPath() == null) {
            return;
        }
        try {
            Files.deleteIfExists(job.getOutputPath());
        } catch (Exception ignored) {
            // best effort cleanup
        }
    }
}
