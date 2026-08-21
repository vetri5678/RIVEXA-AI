package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.aspect.Auditable;
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
            
            log.info("[ReportController] PDF report generated successfully ({} bytes) for repo '{}'",
                    pdfBytes.length, repo.getRepositoryName());
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (Throwable e) {
            log.error("[ReportController] PDF report generation failed for repo '{}': {}",
                    repo != null ? repo.getRepositoryName() : "unknown", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "PDF report generation failed: " + e.getMessage(), e);
        }
    }

    @GetMapping("/download/excel")
    @PreAuthorize("isAuthenticated()")
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
            
            log.info("[ReportController] Excel report generated successfully ({} bytes) for repo '{}'",
                    excelBytes.length, repo.getRepositoryName());
            return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
        } catch (Throwable e) {
            log.error("[ReportController] Excel report generation failed for repo '{}': {}",
                    repo != null ? repo.getRepositoryName() : "unknown", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Excel report generation failed: " + e.getMessage(), e);
        }
    }

    private record ReportContext(RepositoryPredictionEntity prediction, RepositoryEntity repo) {}

    private ReportContext validateAndGetReportContext(String predictionId, String projectId, Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        String targetId = (predictionId != null && !predictionId.isBlank()) ? predictionId : projectId;
        if (targetId == null || targetId.trim().isEmpty()) {
            RepositoryEntity defaultRepo = repositoryRepository.findAll().stream().findFirst().orElse(null);
            if (defaultRepo != null) {
                targetId = defaultRepo.getId().toString();
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing prediction_id or project_id parameter");
            }
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
            // Try matching repo by first available
            repo = repositoryRepository.findAll().stream().findFirst().orElse(null);
            if (repo == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository not found for ID: " + targetId);
            }
        }

        if (prediction == null) {
            // Synthesize baseline prediction for repository that has no prediction run recorded yet
            prediction = RepositoryPredictionEntity.builder()
                    .id(UUID.randomUUID())
                    .repositoryId(repo.getId())
                    .failureProbability(repo.getFailureProbability() != null ? repo.getFailureProbability() : 0.1)
                    .riskScore(repo.getHealthScore() != null ? (int) Math.round(100 - repo.getHealthScore()) : 10)
                    .riskLevel(repo.getRiskLevel() != null ? repo.getRiskLevel() : "LOW")
                    .confidence(repo.getAiConfidence() != null ? repo.getAiConfidence() : 0.8)
                    .healthScore(repo.getHealthScore() != null ? repo.getHealthScore() : 90.0)
                    .modelVersion("xgboost-v2.x")
                    .predictionStatus(repo.getPredictionStatus() != null ? repo.getPredictionStatus() : "PENDING")
                    .triggeredBy("SYSTEM_REPORT")
                    .createdAt(java.time.LocalDateTime.now())
                    .build();
        }

        return new ReportContext(prediction, repo);
    }
}
