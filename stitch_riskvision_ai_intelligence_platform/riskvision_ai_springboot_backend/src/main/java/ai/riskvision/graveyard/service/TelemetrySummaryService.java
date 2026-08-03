package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.entity.RiskMetricsEntity;
import ai.riskvision.graveyard.entity.SystemMetricsEntity;
import ai.riskvision.graveyard.entity.TelemetryMetricsEntity;
import ai.riskvision.graveyard.repository.RepositoryEntityRepository;
import ai.riskvision.graveyard.repository.RiskMetricsEntityRepository;
import ai.riskvision.graveyard.repository.SystemMetricsEntityRepository;
import ai.riskvision.graveyard.repository.TelemetryMetricsEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelemetrySummaryService {

    private final AIAnalysisService aiAnalysisService;
    private final SystemMetricsEntityRepository systemMetricsRepository;
    private final TelemetryMetricsEntityRepository telemetryMetricsRepository;
    private final RiskMetricsEntityRepository riskMetricsRepository;
    private final RepositoryEntityRepository repoRepository;

    public String analyzeTelemetry() {
        return aiAnalysisService.analyze("telemetry_analysis", getTelemetryContext());
    }

    public SseEmitter streamAnalyzeTelemetry() {
        return aiAnalysisService.streamAnalyze("telemetry_analysis", getTelemetryContext());
    }

    public String generateExecutiveSummary() {
        return aiAnalysisService.analyze("executive_summary", getGlobalOverviewContext());
    }

    public SseEmitter streamGenerateExecutiveSummary() {
        return aiAnalysisService.streamAnalyze("executive_summary", getGlobalOverviewContext());
    }

    public String generateWeeklySummary() {
        return aiAnalysisService.analyze("weekly_summary", getWeeklyTrendContext());
    }

    public SseEmitter streamGenerateWeeklySummary() {
        return aiAnalysisService.streamAnalyze("weekly_summary", getWeeklyTrendContext());
    }

    private Map<String, Object> getTelemetryContext() {
        List<SystemMetricsEntity> sysList = systemMetricsRepository.findAll(
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "timestamp"))
        ).getContent();
        SystemMetricsEntity sys = sysList.isEmpty() ? null : sysList.get(0);

        List<TelemetryMetricsEntity> telList = telemetryMetricsRepository.findAll(
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "timestamp"))
        ).getContent();
        TelemetryMetricsEntity tel = telList.isEmpty() ? null : telList.get(0);

        Map<String, Object> ctx = new HashMap<>();
        if (sys != null) {
            ctx.put("system_cpu_usage_pct", sys.getCpuUsage());
            ctx.put("system_memory_usage_pct", sys.getMemoryUsage());
            ctx.put("system_disk_usage_pct", sys.getDiskUsage());
            ctx.put("database_latency_ms", sys.getApiResponseTimeMs());
            ctx.put("inference_latency_ms", sys.getModelInferenceTimeMs());
            ctx.put("system_running_threads", sys.getRunningThreads());
        }
        if (tel != null) {
            ctx.put("vcs_commits_today", tel.getCommitsCount());
            ctx.put("vcs_pull_requests_today", tel.getPullRequestsCount());
            ctx.put("ci_failed_builds_today", tel.getFailedBuildsCount());
            ctx.put("ci_successful_builds_today", tel.getSuccessfulBuildsCount());
        }
        return ctx;
    }

    private Map<String, Object> getGlobalOverviewContext() {
        long totalRepos = repoRepository.count();
        long critical = repoRepository.countByRiskLevel("CRITICAL");
        long high = repoRepository.countByRiskLevel("HIGH");
        long medium = repoRepository.countByRiskLevel("MEDIUM");
        long low = repoRepository.countByRiskLevel("LOW");

        Double avgFailProb = repoRepository.avgFailureProbability();
        Double avgConfidence = repoRepository.avgAiConfidence();

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("total_repositories", totalRepos);
        ctx.put("critical_risk_repositories", critical);
        ctx.put("high_risk_repositories", high);
        ctx.put("medium_risk_repositories", medium);
        ctx.put("low_risk_repositories", low);
        ctx.put("average_failure_probability", avgFailProb != null ? avgFailProb : 0.35);
        ctx.put("average_ai_model_confidence", avgConfidence != null ? avgConfidence : 0.94);
        return ctx;
    }

    private Map<String, Object> getWeeklyTrendContext() {
        List<RiskMetricsEntity> trendList = riskMetricsRepository.findAll(
                PageRequest.of(0, 7, Sort.by(Sort.Direction.DESC, "timestamp"))
        ).getContent();

        Map<String, Object> ctx = new HashMap<>();
        for (int i = 0; i < trendList.size(); i++) {
            RiskMetricsEntity m = trendList.get(i);
            String prefix = "day_" + (i + 1) + "_";
            ctx.put(prefix + "graveyard_index", m.getGraveyardIndex());
            ctx.put(prefix + "critical_repos", m.getCriticalCount());
            ctx.put(prefix + "at_risk_repos", m.getAtRiskCount());
        }
        return ctx;
    }
}
