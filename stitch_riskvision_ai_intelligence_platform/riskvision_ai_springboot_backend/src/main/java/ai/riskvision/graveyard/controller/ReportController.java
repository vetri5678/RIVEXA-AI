package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.aspect.Auditable;
import ai.riskvision.graveyard.service.N8nWebhookService;
import ai.riskvision.graveyard.service.ReportGenerationService;
import ai.riskvision.graveyard.service.PdfReportService;
import ai.riskvision.graveyard.service.ExcelReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.UUID;
import ai.riskvision.graveyard.entity.RepositoryEntity;
import ai.riskvision.graveyard.entity.RepositoryPredictionEntity;
import lombok.extern.slf4j.Slf4j;
import ai.riskvision.graveyard.repository.RepositoryPredictionEntityRepository;
import ai.riskvision.graveyard.repository.RepositoryEntityRepository;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Slf4j
public class ReportController {

    private final PdfReportService pdfReportService;
    private final ExcelReportService excelReportService;
    private final ReportGenerationService reportGenerationService;
    private final RepositoryPredictionEntityRepository predictionRepository;
    private final RepositoryEntityRepository repositoryRepository;
    private final N8nWebhookService n8nWebhookService;

    @GetMapping("/executive/json")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'ANALYST')")
    @Auditable(action = "REPORT_EXPORT_EXECUTIVE", module = "REPORT", severity = "LOW")
    public ResponseEntity<byte[]> exportExecutiveSummaryJson() {
        byte[] data = reportGenerationService.generateExecutiveSummaryJson();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=executive_summary.json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(data);
    }

    @GetMapping("/projects/csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'ANALYST')")
    @Auditable(action = "REPORT_EXPORT_PROJECTS", module = "REPORT", severity = "LOW")
    public ResponseEntity<byte[]> exportProjectsCsv() {
        byte[] data = reportGenerationService.generateProjectsCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=projects_report.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(data);
    }

    @GetMapping("/export/zip")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "REPORT_EXPORT_ZIP", module = "REPORT", severity = "MEDIUM")
    public ResponseEntity<byte[]> exportZipPackage() {
        byte[] data = reportGenerationService.generateZipPackage();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=riskvision_reports_bundle.zip")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(data);
    }

    @GetMapping("/download/pdf")
    @PreAuthorize("isAuthenticated()")
    @Auditable(action = "REPORT_EXPORT_PDF", module = "REPORT", severity = "LOW")
    public ResponseEntity<byte[]> downloadPdf(
            @RequestParam(value = "prediction_id", required = false) String predictionId,
            @RequestParam(value = "project_id", required = false) String projectId,
            Principal principal) {
        
        log.info("[ReportController] PDF download requested with prediction_id={}, project_id={}, user={}",
                predictionId, projectId, principal != null ? principal.getName() : "anonymous");
        
        ReportContext context = validateAndGetReportContext(predictionId, projectId, principal);
        RepositoryEntity repo = context.repo;
        RepositoryPredictionEntity prediction = context.prediction;
        
        try {
            log.info("[ReportController] Generating PDF report for repo '{}' (ID: {})", repo.getRepositoryName(), repo.getId());
            byte[] pdfBytes = pdfReportService.generatePredictionPdf(prediction, repo);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            
            String safeName = (repo.getRepositoryName() != null ? repo.getRepositoryName() : "Report")
                    .replaceAll("[^a-zA-Z0-9-_]", "_");
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"RiskVision_" + safeName + "_Risk_Report.pdf\"");
            
            if (n8nWebhookService != null) {
                try {
                    n8nWebhookService.triggerReportGeneratedWebhook(
                            prediction != null ? prediction.getId().toString() : UUID.randomUUID().toString(),
                            repo != null ? repo.getId().toString() : projectId,
                            "PREDICTION_RISK",
                            "PDF",
                            principal != null ? principal.getName() : "SYSTEM"
                    );
                } catch (Exception ex) {
                    log.warn("[ReportController] Non-critical error triggering report webhook: {}", ex.getMessage());
                }
            }

            log.info("[ReportController] PDF report generated successfully ({} bytes) for repo '{}'",
                    pdfBytes.length, repo.getRepositoryName());
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Throwable e) {
            log.error("[ReportController] PDF report generation failed for repo '{}': {}",
                    repo != null ? repo.getRepositoryName() : "unknown", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "PDF report generation failed: " + e.getMessage(), e);
        }
    }

    @GetMapping("/download/excel")
    @PreAuthorize("isAuthenticated()")
    @Auditable(action = "REPORT_EXPORT_EXCEL", module = "REPORT", severity = "LOW")
    public ResponseEntity<byte[]> downloadExcel(
            @RequestParam(value = "prediction_id", required = false) String predictionId,
            @RequestParam(value = "project_id", required = false) String projectId,
            Principal principal) {
        
        log.info("[ReportController] Excel download requested with prediction_id={}, project_id={}, user={}",
                predictionId, projectId, principal != null ? principal.getName() : "anonymous");
        
        ReportContext context = validateAndGetReportContext(predictionId, projectId, principal);
        RepositoryEntity repo = context.repo;
        RepositoryPredictionEntity prediction = context.prediction;
        
        try {
            log.info("[ReportController] Generating Excel report for repo '{}' (ID: {})", repo.getRepositoryName(), repo.getId());
            byte[] excelBytes = excelReportService.generatePredictionExcel(prediction, repo);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            
            String safeName = (repo.getRepositoryName() != null ? repo.getRepositoryName() : "Report")
                    .replaceAll("[^a-zA-Z0-9-_]", "_");
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"RiskVision_" + safeName + "_Risk_Report.xlsx\"");
            
            if (n8nWebhookService != null) {
                try {
                    n8nWebhookService.triggerReportGeneratedWebhook(
                            prediction != null ? prediction.getId().toString() : UUID.randomUUID().toString(),
                            repo != null ? repo.getId().toString() : projectId,
                            "PREDICTION_RISK",
                            "EXCEL",
                            principal != null ? principal.getName() : "SYSTEM"
                    );
                } catch (Exception ex) {
                    log.warn("[ReportController] Non-critical error triggering report webhook: {}", ex.getMessage());
                }
            }
            
            log.info("[ReportController] Excel report generated successfully ({} bytes) for repo '{}'",
                    excelBytes.length, repo.getRepositoryName());
            return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Throwable e) {
            log.error("[ReportController] Excel report generation failed for repo '{}': {}",
                    repo != null ? repo.getRepositoryName() : "unknown", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Excel report generation failed: " + e.getMessage(), e);
        }
    }

    @PostMapping("/batch/zip")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'ANALYST')")
    @Auditable(action = "REPORT_EXPORT_BATCH_ZIP", module = "REPORT", severity = "MEDIUM")
    public ResponseEntity<byte[]> exportBatchZip(@RequestBody(required = false) java.util.List<String> repositoryIds) {
        log.info("[ReportController] Batch ZIP export requested for {} repositories",
                repositoryIds != null ? repositoryIds.size() : 0);
        byte[] zipBytes = reportGenerationService.generateBatchZipPackageForRepos(repositoryIds);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"RiskVision_Batch_Reports.zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zipBytes);
    }

    private static record ReportContext(RepositoryPredictionEntity prediction, RepositoryEntity repo) {}

    private ReportContext validateAndGetReportContext(String predictionId, String projectId, Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        String targetId = (predictionId != null && !predictionId.isBlank()) ? predictionId : projectId;
        if (targetId == null || targetId.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing prediction_id or project_id parameter");
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(targetId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed prediction/project ID: " + targetId);
        }

        RepositoryPredictionEntity prediction = predictionRepository.findById(uuid).orElse(null);
        RepositoryEntity repo = null;

        if (prediction != null) {
            repo = repositoryRepository.findById(prediction.getRepositoryId()).orElse(null);
        } else {
            repo = repositoryRepository.findById(uuid).orElse(null);
            if (repo != null) {
                prediction = predictionRepository.findTopByRepositoryIdOrderByCreatedAtDesc(uuid).orElse(null);
            }
        }

        if (repo == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository not found for ID: " + targetId);
        }

        if (prediction == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No prediction analysis data exists for repository: " + repo.getRepositoryName() +
                            ". Please run an AI prediction analysis before downloading reports.");
        }

        return new ReportContext(prediction, repo);
    }
}
