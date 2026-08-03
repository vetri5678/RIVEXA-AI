package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.entity.RepositoryEntity;
import ai.riskvision.graveyard.entity.RepositoryMetricsEntity;
import ai.riskvision.graveyard.repository.RepositoryEntityRepository;
import ai.riskvision.graveyard.repository.RepositoryMetricsEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepositoryAnalysisService {

    private final AIAnalysisService aiAnalysisService;
    private final RepositoryEntityRepository repoRepository;
    private final RepositoryMetricsEntityRepository metricsRepository;

    public String analyzeRepository(UUID repoId) {
        return aiAnalysisService.analyze("repository_risk_analysis", getRepoContext(repoId));
    }

    public SseEmitter streamAnalyzeRepository(UUID repoId) {
        return aiAnalysisService.streamAnalyze("repository_risk_analysis", getRepoContext(repoId));
    }

    public String generateSecuritySummary(UUID repoId) {
        return aiAnalysisService.analyze("security_summary", getRepoContext(repoId));
    }

    public SseEmitter streamGenerateSecuritySummary(UUID repoId) {
        return aiAnalysisService.streamAnalyze("security_summary", getRepoContext(repoId));
    }

    public String getGithubInsights(UUID repoId) {
        return aiAnalysisService.analyze("github_repository_insights", getRepoContext(repoId));
    }

    public SseEmitter streamGetGithubInsights(UUID repoId) {
        return aiAnalysisService.streamAnalyze("github_repository_insights", getRepoContext(repoId));
    }

    public String explainCodeRisk(UUID repoId) {
        return aiAnalysisService.analyze("code_risk_explanation", getRepoContext(repoId));
    }

    public SseEmitter streamExplainCodeRisk(UUID repoId) {
        return aiAnalysisService.streamAnalyze("code_risk_explanation", getRepoContext(repoId));
    }

    public String generateRecommendations(UUID repoId) {
        return aiAnalysisService.analyze("recommendations", getRepoContext(repoId));
    }

    public SseEmitter streamGenerateRecommendations(UUID repoId) {
        return aiAnalysisService.streamAnalyze("recommendations", getRepoContext(repoId));
    }

    private Map<String, Object> getRepoContext(UUID repoId) {
        RepositoryEntity repo = repoRepository.findById(repoId)
                .orElseThrow(() -> new NoSuchElementException("Repository not found: " + repoId));

        RepositoryMetricsEntity metrics = metricsRepository.findByRepositoryId(repoId).orElse(null);

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("repository_name", repo.getRepositoryName());
        ctx.put("owner", repo.getOwner());
        ctx.put("organization", repo.getOrganization());
        ctx.put("git_url", repo.getRepositoryUrl());
        ctx.put("language", repo.getLanguage());
        ctx.put("technology", repo.getTechnology());
        ctx.put("lifecycle_stage", repo.getLifecycleStage());
        ctx.put("status", repo.getStatus());
        ctx.put("risk_level", repo.getRiskLevel());
        ctx.put("failure_probability", repo.getFailureProbability());
        ctx.put("health_score", repo.getHealthScore());

        if (metrics != null) {
            ctx.put("commits_count", metrics.getCommitCount());
            ctx.put("pull_requests", metrics.getPullRequests());
            ctx.put("merged_prs", metrics.getMergedPullRequests());
            ctx.put("failed_prs", metrics.getFailedPullRequests());
            ctx.put("total_contributors", metrics.getContributors());
            ctx.put("active_contributors", metrics.getActiveContributors());
            ctx.put("inactive_days", metrics.getInactiveDays());
            ctx.put("open_issues", metrics.getOpenIssues());
            ctx.put("closed_issues", metrics.getClosedIssues());
            ctx.put("code_coverage_pct", metrics.getCodeCoverage());
            ctx.put("documentation_score", metrics.getDocumentationScore());
            ctx.put("build_success_rate_pct", metrics.getBuildSuccessRate());
            ctx.put("cyclomatic_complexity", metrics.getCyclomaticComplexity());
            ctx.put("technical_debt_hours", metrics.getTechnicalDebt());
            ctx.put("bus_factor", metrics.getBusFactor());
            ctx.put("commit_velocity_per_week", metrics.getVelocity());
        }
        return ctx;
    }
}
