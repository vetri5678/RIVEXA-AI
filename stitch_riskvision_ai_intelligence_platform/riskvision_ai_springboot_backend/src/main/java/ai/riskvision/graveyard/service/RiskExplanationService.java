package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.entity.RepositoryEntity;
import ai.riskvision.graveyard.entity.RepositoryMetricsEntity;
import ai.riskvision.graveyard.entity.RepositoryPredictionEntity;
import ai.riskvision.graveyard.repository.RepositoryEntityRepository;
import ai.riskvision.graveyard.repository.RepositoryMetricsEntityRepository;
import ai.riskvision.graveyard.repository.RepositoryPredictionEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class RiskExplanationService {

    private final AIAnalysisService aiAnalysisService;
    private final RepositoryEntityRepository repoRepository;
    private final RepositoryPredictionEntityRepository predictionRepository;
    private final RepositoryMetricsEntityRepository metricsRepository;

    public String explainPrediction(UUID repoId) {
        return aiAnalysisService.analyze("prediction_explanation", getPredictionContext(repoId));
    }

    public SseEmitter streamExplainPrediction(UUID repoId) {
        return aiAnalysisService.streamAnalyze("prediction_explanation", getPredictionContext(repoId));
    }

    public String explainModelConfidence(UUID repoId) {
        return aiAnalysisService.analyze("model_confidence_explanation", getPredictionContext(repoId));
    }

    public SseEmitter streamExplainModelConfidence(UUID repoId) {
        return aiAnalysisService.streamAnalyze("model_confidence_explanation", getPredictionContext(repoId));
    }

    public String explainThreat(UUID repoId) {
        return aiAnalysisService.analyze("threat_explanation", getPredictionContext(repoId));
    }

    public SseEmitter streamExplainThreat(UUID repoId) {
        return aiAnalysisService.streamAnalyze("threat_explanation", getPredictionContext(repoId));
    }

    private Map<String, Object> getPredictionContext(UUID repoId) {
        RepositoryEntity repo = repoRepository.findById(repoId)
                .orElseThrow(() -> new NoSuchElementException("Repository not found: " + repoId));

        RepositoryMetricsEntity metrics = metricsRepository.findByRepositoryId(repoId).orElse(null);

        List<RepositoryPredictionEntity> preds = predictionRepository.findByRepositoryIdOrderByCreatedAtDesc(repoId);
        RepositoryPredictionEntity latestPred = preds.isEmpty() ? null : preds.get(0);

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("repository_name", repo.getRepositoryName());
        ctx.put("language", repo.getLanguage());
        ctx.put("technology", repo.getTechnology());
        ctx.put("lifecycle_stage", repo.getLifecycleStage());
        ctx.put("current_failure_probability", repo.getFailureProbability());
        ctx.put("current_risk_level", repo.getRiskLevel());
        ctx.put("current_health_score", repo.getHealthScore());
        ctx.put("ai_confidence", repo.getAiConfidence());

        if (metrics != null) {
            ctx.put("total_commits", metrics.getCommitCount());
            ctx.put("total_pull_requests", metrics.getPullRequests());
            ctx.put("failed_pull_requests", metrics.getFailedPullRequests());
            ctx.put("active_contributors", metrics.getActiveContributors());
            ctx.put("inactive_days_count", metrics.getInactiveDays());
            ctx.put("unit_test_coverage_pct", metrics.getCodeCoverage());
            ctx.put("build_success_rate_pct", metrics.getBuildSuccessRate());
            ctx.put("technical_debt_hours", metrics.getTechnicalDebt());
            ctx.put("bus_factor", metrics.getBusFactor());
        }

        if (latestPred != null) {
            ctx.put("feature_importance_weights", latestPred.getFeatureImportanceJson());
            ctx.put("recommender_heuristic_actions", latestPred.getRecommendationsJson());
        }

        return ctx;
    }
}
