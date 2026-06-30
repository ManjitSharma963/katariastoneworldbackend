package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.AuditReportStatusDTO;
import com.katariastoneworld.apis.entity.User;
import com.katariastoneworld.apis.repository.UserRepository;
import com.katariastoneworld.apis.service.audit.AuditReportJob;
import com.katariastoneworld.apis.service.audit.AuditReportJobManager;
import com.katariastoneworld.apis.util.RequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping({"/api/reports/audit", "/reports/audit"})
@Tag(name = "Audit Report", description = "Generate a complete application data snapshot PDF for audits")
public class AuditReportController {

    private final AuditReportJobManager jobManager;
    private final UserRepository userRepository;

    public AuditReportController(AuditReportJobManager jobManager, UserRepository userRepository) {
        this.jobManager = jobManager;
        this.userRepository = userRepository;
    }

    @Operation(summary = "Start audit report generation",
            description = "Admin-only. Starts an asynchronous job that builds a full application audit PDF.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/start")
    @RequiresRole("admin")
    public ResponseEntity<Map<String, String>> startAuditReport(HttpServletRequest request) {
        Long userId = RequestUtil.getUserIdFromRequest(request);
        String location = RequestUtil.getLocationFromRequest(request);
        User user = userId != null ? userRepository.findById(userId).orElse(null) : null;
        String name = user != null ? user.getName() : "Administrator";
        String email = user != null ? user.getEmail() : "";
        String jobId = jobManager.startJob(userId, location, name, email);
        return ResponseEntity.accepted().body(Map.of("jobId", jobId));
    }

    @Operation(summary = "Audit report job status")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/status/{jobId}")
    @RequiresRole("admin")
    public ResponseEntity<AuditReportStatusDTO> status(@PathVariable String jobId, HttpServletRequest request) {
        AuditReportJob job = requireOwnedJob(jobId, request);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(jobManager.toStatusDto(job));
    }

    @Operation(summary = "Download completed audit report PDF")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping(value = "/download/{jobId}", produces = MediaType.APPLICATION_PDF_VALUE)
    @RequiresRole("admin")
    public ResponseEntity<Resource> download(@PathVariable String jobId, HttpServletRequest request) {
        AuditReportJob job = requireOwnedJob(jobId, request);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        if (job.getStatus().get() != AuditReportJob.Status.COMPLETED || job.getOutputPath() == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        Path path = job.getOutputPath();
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        String filename = job.getFilename().get();
        if (filename == null || filename.isBlank()) {
            filename = path.getFileName().toString();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        try {
            headers.setContentLength(Files.size(path));
        } catch (Exception ignored) {
            // optional
        }
        return ResponseEntity.ok().headers(headers).body(new FileSystemResource(path));
    }

    private AuditReportJob requireOwnedJob(String jobId, HttpServletRequest request) {
        AuditReportJob job = jobManager.getJob(jobId);
        if (job == null) {
            return null;
        }
        Long userId = RequestUtil.getUserIdFromRequest(request);
        if (userId != null && job.getUserId() != null && !userId.equals(job.getUserId())) {
            return null;
        }
        return job;
    }
}
