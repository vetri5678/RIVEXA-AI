package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.entity.RepositoryEntity;
import ai.riskvision.graveyard.entity.RepositoryPredictionEntity;
import ai.riskvision.graveyard.repository.AuditLogRepository;
import ai.riskvision.graveyard.repository.ProjectRepository;
import ai.riskvision.graveyard.repository.RepositoryEntityRepository;
import ai.riskvision.graveyard.repository.RepositoryPredictionEntityRepository;
import ai.riskvision.graveyard.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportGenerationService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final RepositoryEntityRepository repositoryEntityRepository;
    private final RepositoryPredictionEntityRepository predictionRepository;
    private final AuditLogRepository auditLogRepository;
    private final PdfReportService pdfReportService;
    private final ExcelReportService excelReportService;
    private final ObjectMapper objectMapper;
    private final N8nWebhookService n8nWebhookService;

    public byte[] generateExecutiveSummaryJson() {
        try {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("report_title", "RiskVision AI Executive Platform Report");
            summary.put("generated_at", LocalDateTime.now().toString());
            summary.put("total_users", userRepository.count());
            summary.put("total_projects", projectRepository.count());
            summary.put("total_repositories", repositoryEntityRepository.count());
            summary.put("total_audit_logs", auditLogRepository.count());

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to generate Executive JSON report", e);
            throw new RuntimeException("Report generation failed", e);
        }
    }

    public byte[] generateProjectsCsv() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(out)) {
            writer.println("ID,ExternalID,Name,Status,Budget,ActualCost,TimelineMonths,ActualDuration,CreatedDate");
            projectRepository.findAll().forEach(p -> {
                writer.printf("%s,\"%s\",\"%s\",\"%s\",%.2f,%.2f,%.1f,%.1f,\"%s\"%n",
                        p.getId(), p.getExternalId(), p.getName(), p.getStatus(),
                        p.getBudget() != null ? p.getBudget() : 0.0,
                        p.getActualCost() != null ? p.getActualCost() : 0.0,
                        p.getTimelineMonths() != null ? p.getTimelineMonths() : 0.0,
                        p.getActualDuration() != null ? p.getActualDuration() : 0.0,
                        p.getCreatedAt());
            });
            writer.flush();
        }
        return out.toByteArray();
    }

    public byte[] generateZipPackage() {
        return generateBatchZipPackageForRepos(null);
    }

    public byte[] generateBatchZipPackageForRepos(List<String> repositoryIds) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Entry 1: Executive Summary JSON
            ZipEntry summaryEntry = new ZipEntry("executive_summary.json");
            zos.putNextEntry(summaryEntry);
            zos.write(generateExecutiveSummaryJson());
            zos.closeEntry();

            // Entry 2: Projects CSV
            ZipEntry projectsEntry = new ZipEntry("projects_report.csv");
            zos.putNextEntry(projectsEntry);
            zos.write(generateProjectsCsv());
            zos.closeEntry();

            // Determine repositories to include
            List<RepositoryEntity> targetRepos;
            if (repositoryIds != null && !repositoryIds.isEmpty()) {
                targetRepos = new ArrayList<>();
                for (String idStr : repositoryIds) {
                    try {
                        UUID uuid = UUID.fromString(idStr);
                        repositoryEntityRepository.findById(uuid).ifPresent(targetRepos::add);
                    } catch (Exception ignored) {}
                }
            } else {
                targetRepos = repositoryEntityRepository.findAll();
            }

            // Include PDF & Excel for each repository with real predictions
            for (RepositoryEntity repo : targetRepos) {
                Optional<RepositoryPredictionEntity> predOpt = predictionRepository.findTopByRepositoryIdOrderByCreatedAtDesc(repo.getId());
                if (predOpt.isPresent()) {
                    RepositoryPredictionEntity pred = predOpt.get();
                    String safeName = (repo.getRepositoryName() != null ? repo.getRepositoryName() : "Repo_" + repo.getId())
                            .replaceAll("[^a-zA-Z0-9-_]", "_");

                    try {
                        byte[] pdfBytes = pdfReportService.generatePredictionPdf(pred, repo);
                        ZipEntry pdfEntry = new ZipEntry("reports/pdf/RiskVision_" + safeName + "_Risk_Report.pdf");
                        zos.putNextEntry(pdfEntry);
                        zos.write(pdfBytes);
                        zos.closeEntry();

                        if (n8nWebhookService != null) {
                            n8nWebhookService.triggerReportGeneratedWebhook(
                                    "pdf-" + repo.getId(),
                                    repo.getId().toString(),
                                    "EXECUTIVE_RISK",
                                    "PDF",
                                    "SYSTEM"
                            );
                        }
                    } catch (Exception e) {
                        log.warn("Failed to generate PDF for batch zip repo {}: {}", repo.getId(), e.getMessage());
                    }

                    try {
                        byte[] excelBytes = excelReportService.generatePredictionExcel(pred, repo);
                        ZipEntry excelEntry = new ZipEntry("reports/excel/RiskVision_" + safeName + "_Risk_Report.xlsx");
                        zos.putNextEntry(excelEntry);
                        zos.write(excelBytes);
                        zos.closeEntry();

                        if (n8nWebhookService != null) {
                            n8nWebhookService.triggerReportGeneratedWebhook(
                                    "excel-" + repo.getId(),
                                    repo.getId().toString(),
                                    "EXECUTIVE_METRICS",
                                    "EXCEL",
                                    "SYSTEM"
                            );
                        }
                    } catch (Exception e) {
                        log.warn("Failed to generate Excel for batch zip repo {}: {}", repo.getId(), e.getMessage());
                    }
                }
            }

            zos.finish();
        } catch (Exception e) {
            log.error("Failed to generate ZIP report bundle", e);
            throw new RuntimeException("ZIP package generation failed", e);
        }

        if (n8nWebhookService != null) {
            n8nWebhookService.triggerReportGeneratedWebhook(
                    "zip-package-" + System.currentTimeMillis(),
                    "ALL",
                    "BATCH_PACKAGE",
                    "ZIP",
                    "SYSTEM"
            );
        }

        return baos.toByteArray();
    }
}
