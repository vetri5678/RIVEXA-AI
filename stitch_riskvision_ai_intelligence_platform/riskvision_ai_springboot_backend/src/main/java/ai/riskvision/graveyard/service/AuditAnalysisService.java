package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.entity.AuditLogEntity;
import ai.riskvision.graveyard.entity.RepositoryEntity;
import ai.riskvision.graveyard.entity.RepositoryMetricsEntity;
import ai.riskvision.graveyard.repository.AuditLogRepository;
import ai.riskvision.graveyard.repository.RepositoryEntityRepository;
import ai.riskvision.graveyard.repository.RepositoryMetricsEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditAnalysisService {

    private final AIAnalysisService aiAnalysisService;
    private final AuditLogRepository auditLogRepository;
    private final RepositoryEntityRepository repoRepository;
    private final RepositoryMetricsEntityRepository metricsRepository;

    public String explainEvent(Map<String, Object> eventData) {
        return aiAnalysisService.analyze("event_log_summary", eventData);
    }

    public SseEmitter streamExplainEvent(Map<String, Object> eventData) {
        return aiAnalysisService.streamAnalyze("event_log_summary", eventData);
    }

    public String explainSystemAudits() {
        return aiAnalysisService.analyze("system_audit_explanation", getAuditLogsContext());
    }

    public SseEmitter streamExplainSystemAudits() {
        return aiAnalysisService.streamAnalyze("system_audit_explanation", getAuditLogsContext());
    }

    public String explainDeploymentFailure(UUID repoId) {
        return aiAnalysisService.analyze("deployment_failure_explanation", getRepositoryFailureContext(repoId));
    }

    public SseEmitter streamExplainDeploymentFailure(UUID repoId) {
        return aiAnalysisService.streamAnalyze("deployment_failure_explanation", getRepositoryFailureContext(repoId));
    }

    public String generateIncidentReport(UUID repoId) {
        return aiAnalysisService.analyze("incident_report", getRepositoryFailureContext(repoId));
    }

    public SseEmitter streamGenerateIncidentReport(UUID repoId) {
        return aiAnalysisService.streamAnalyze("incident_report", getRepositoryFailureContext(repoId));
    }

    private Map<String, Object> getAuditLogsContext() {
        List<AuditLogEntity> logs = auditLogRepository.findAll(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();

        Map<String, Object> ctx = new HashMap<>();
        for (int i = 0; i < logs.size(); i++) {
            AuditLogEntity logItem = logs.get(i);
            String prefix = "log_" + (i + 1) + "_";
            ctx.put(prefix + "event", logItem.getEventType());
            ctx.put(prefix + "desc", logItem.getDetails());
            ctx.put(prefix + "status", logItem.getStatus());
            ctx.put(prefix + "ip", logItem.getIpAddress());
        }
        return ctx;
    }

    private Map<String, Object> getRepositoryFailureContext(UUID repoId) {
        RepositoryEntity repo = repoRepository.findById(repoId)
                .orElseThrow(() -> new NoSuchElementException("Repository not found: " + repoId));

        RepositoryMetricsEntity metrics = metricsRepository.findByRepositoryId(repoId).orElse(null);

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("repository_name", repo.getRepositoryName());
        ctx.put("git_url", repo.getRepositoryUrl());
        ctx.put("language", repo.getLanguage());
        ctx.put("owner", repo.getOwner());
        ctx.put("risk_level", repo.getRiskLevel());

        if (metrics != null) {
            ctx.put("failed_build_prs", metrics.getFailedPullRequests());
            ctx.put("build_success_rate", metrics.getBuildSuccessRate());
            ctx.put("inactive_days", metrics.getInactiveDays());
            ctx.put("coverage", metrics.getCodeCoverage());
            ctx.put("technical_debt_hours", metrics.getTechnicalDebt());
        }
        return ctx;
    }
}
