package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.repository.AuditLogRepository;
import ai.riskvision.graveyard.repository.ProjectRepository;
import ai.riskvision.graveyard.repository.RepositoryEntityRepository;
import ai.riskvision.graveyard.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportGenerationService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final RepositoryEntityRepository repositoryEntityRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

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
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Entry 1: Executive Summary
            ZipEntry summaryEntry = new ZipEntry("executive_summary.json");
            zos.putNextEntry(summaryEntry);
            zos.write(generateExecutiveSummaryJson());
            zos.closeEntry();

            // Entry 2: Projects CSV
            ZipEntry projectsEntry = new ZipEntry("projects_report.csv");
            zos.putNextEntry(projectsEntry);
            zos.write(generateProjectsCsv());
            zos.closeEntry();

            zos.finish();
        } catch (Exception e) {
            log.error("Failed to generate ZIP report bundle", e);
            throw new RuntimeException("ZIP package generation failed", e);
        }
        return baos.toByteArray();
    }
}
