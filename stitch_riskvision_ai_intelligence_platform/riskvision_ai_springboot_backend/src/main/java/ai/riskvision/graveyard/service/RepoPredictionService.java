package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.entity.RepositoryEntity;
import ai.riskvision.graveyard.entity.RepositoryPredictionEntity;
import ai.riskvision.graveyard.repository.RepositoryEntityRepository;
import ai.riskvision.graveyard.repository.RepositoryPredictionEntityRepository;
import ai.riskvision.graveyard.repository.RepositoryMetricsEntityRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepoPredictionService {

    private final RepositoryEntityRepository repoRepository;
    private final RepositoryPredictionEntityRepository predictionRepository;
    private final RepositoryMetricsEntityRepository metricsRepository;
    private final RepositorySyncService syncService;
    private final ObjectMapper objectMapper;

    @Value("${ml.service.url:http://localhost:5000}")
    private String mlServiceUrl;

    @Value("${llm.service.url:http://localhost:5001}")
    private String llmServiceUrl;

    private final RestTemplate restTemplate;

    /**
     * Runs an AI prediction for the given repository.
     * Computes failure probability by calling the Python FastAPI ML Service.
     * Falls back to local heuristic calculations if the ML Service is unreachable.
     */
    @Transactional
    public RepositoryPredictionEntity runPrediction(UUID repositoryId, String actor) {
        if (repositoryId == null) {
            throw new IllegalArgumentException("Repository ID must not be null");
        }
        log.info("[RepoPredictionService] Starting prediction for repositoryId={} actor={}", repositoryId, actor);

        RepositoryEntity entity = repoRepository.findById(repositoryId)
                .orElseThrow(() -> {
                    log.warn("[RepoPredictionService] Repository not found in database: {}", repositoryId);
                    return new NoSuchElementException("Repository not found: " + repositoryId);
                });

        log.debug("[RepoPredictionService] Repository loaded: name={} status={} lifecycleStage={}",
                entity.getRepositoryName(), entity.getStatus(), entity.getLifecycleStage());

        double failureProb = 0.0;
        double confidence = 0.70;
        String riskLevel = "LOW";
        String featureJson = null;
        String recommendationsJson = null;
        String modelVersion = "graveyard-ml-v1.0";

        try {
            String url = mlServiceUrl + "/api/v1/pipeline/predict";
            log.info("[RepoPredictionService] Calling FastAPI ML service — url={} repositoryId={}", url, repositoryId);

            ai.riskvision.graveyard.entity.RepositoryMetricsEntity metrics = metricsRepository.findByRepositoryId(repositoryId).orElse(null);
            log.debug("[RepoPredictionService] Metrics record found={}", metrics != null);

            double budget = 100000.0;
            double actualCost = 30000.0;
            double timelineMonths = 12.0;
            double actualDuration = 3.0;
            double teamSize = 1.0;
            String statusStr = "active";
            double requirementsChanged = 0.0;
            double totalRequirements = 10.0;
            double featuresDelivered = 8.0;
            double identifiedRisks = 0.0;
            double totalTasks = 50.0;

            if (metrics != null) {
                budget = Math.max(50000.0, (metrics.getCommitCount() != null ? metrics.getCommitCount() : 100) * 350.0);
                teamSize = metrics.getActiveContributors() != null ? metrics.getActiveContributors() : 1.0;
                actualCost = teamSize * 15000.0 * (metrics.getInactiveDays() != null && metrics.getInactiveDays() > 30 ? 12.0 : 4.0);
                timelineMonths = Math.max(6.0, (metrics.getCommitCount() != null ? metrics.getCommitCount() : 100) / 100.0);
                actualDuration = timelineMonths * (metrics.getBuildSuccessRate() != null && metrics.getBuildSuccessRate() < 70 ? 1.4 : 0.9);
                statusStr = (entity.getStatus() != null) ? entity.getStatus().toLowerCase() : "active";
                totalRequirements = metrics.getPullRequests() != null ? metrics.getPullRequests() * 1.5 : 50.0;
                featuresDelivered = metrics.getMergedPullRequests() != null ? metrics.getMergedPullRequests() * 1.5 : 40.0;
                requirementsChanged = metrics.getFailedPullRequests() != null ? metrics.getFailedPullRequests() * 2.0 : 5.0;
                identifiedRisks = metrics.getOpenIssues() != null ? metrics.getOpenIssues() : 0.0;
                totalTasks = metrics.getCommitCount() != null ? metrics.getCommitCount() * 1.2 : 120.0;
            } else {
                int contributorsCount = entity.getContributors() != null ? entity.getContributors() : 0;
                teamSize = contributorsCount > 0 ? (double) contributorsCount : 1.0;
                actualCost = teamSize * 12000.0;
                identifiedRisks = entity.getOpenIssues() != null ? (double) entity.getOpenIssues() : 0.0;
            }

            Map<String, Object> request = new HashMap<>();
            request.put("project_id", repositoryId.toString());
            request.put("project_name", entity.getRepositoryName());
            request.put("budget", budget);
            request.put("actual_cost", actualCost);
            request.put("timeline_months", timelineMonths);
            request.put("actual_duration", actualDuration);
            request.put("team_size", teamSize);
            request.put("status", statusStr);
            request.put("requirements_changed", requirementsChanged);
            request.put("total_requirements", totalRequirements);
            request.put("features_delivered", featuresDelivered);
            request.put("identified_risks", identifiedRisks);
            request.put("total_tasks", totalTasks);

            log.debug("[RepoPredictionService] ML payload: project_id={} budget={} teamSize={} status={} timelineMonths={}",
                    repositoryId, budget, teamSize, statusStr, timelineMonths);

            ResponseEntity<Map<String, Object>> responseEntity = restTemplate.exchange(
                    url,
                    Objects.requireNonNull(HttpMethod.POST),
                    new HttpEntity<>(request),
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            Map<String, Object> response = (responseEntity != null) ? responseEntity.getBody() : null;
            if (response != null) {
                failureProb = ((Number) response.get("failure_probability")).doubleValue();
                confidence = ((Number) response.get("confidence_level")).doubleValue();
                riskLevel = (String) response.get("risk_category");

                Object factorsObj = response.get("top_risk_factors");
                if (factorsObj != null) {
                    featureJson = objectMapper.writeValueAsString(factorsObj);
                }

                Object recsObj = response.get("recommended_actions");
                if (recsObj != null) {
                    recommendationsJson = objectMapper.writeValueAsString(recsObj);
                }

                modelVersion = "graveyard-ml-pipeline";
                log.info("[RepoPredictionService] ML Prediction succeeded — repositoryId={} failureProbability={} riskLevel={}",
                        repositoryId, failureProb, riskLevel);
            } else {
                throw new RuntimeException("Empty response body received from FastAPI ML Service at: " + url);
            }
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.warn("[RepoPredictionService] FastAPI ML Service unreachable at {} — Connection refused or service down. " +
                    "Falling back to heuristic engine. Error: {}", mlServiceUrl, e.getMessage());
            failureProb = computeFailureProbability(entity);
            confidence = computeConfidence(entity);
            riskLevel = classifyRisk(failureProb);
            featureJson = buildFeatureImportanceJson(entity, failureProb);
            recommendationsJson = buildRecommendationsJson(entity, riskLevel);
            modelVersion = "graveyard-heuristic-fallback-v1.0";
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.warn("[RepoPredictionService] FastAPI ML Service returned HTTP {} for repositoryId={} — responseBody={}. " +
                    "Falling back to heuristic engine.",
                    e.getStatusCode(), repositoryId, e.getResponseBodyAsString());
            failureProb = computeFailureProbability(entity);
            confidence = computeConfidence(entity);
            riskLevel = classifyRisk(failureProb);
            featureJson = buildFeatureImportanceJson(entity, failureProb);
            recommendationsJson = buildRecommendationsJson(entity, riskLevel);
            modelVersion = "graveyard-heuristic-fallback-v1.0";
        } catch (Exception e) {
            log.warn("[RepoPredictionService] Unexpected error contacting FastAPI ML Service at {} for repositoryId={} — " +
                    "exceptionType={} message={}. Falling back to heuristic engine.",
                    mlServiceUrl, repositoryId, e.getClass().getSimpleName(), e.getMessage());
            failureProb = computeFailureProbability(entity);
            confidence = computeConfidence(entity);
            riskLevel = classifyRisk(failureProb);
            featureJson = buildFeatureImportanceJson(entity, failureProb);
            recommendationsJson = buildRecommendationsJson(entity, riskLevel);
            modelVersion = "graveyard-heuristic-fallback-v1.0";
        }

        int riskScore = (int) Math.round(failureProb * 100);
        double healthScore = Math.max(0, 100.0 - (failureProb * 100.0));

        // Persist prediction record
        RepositoryPredictionEntity prediction = RepositoryPredictionEntity.builder()
                .repositoryId(repositoryId)
                .failureProbability(failureProb)
                .riskScore(riskScore)
                .riskLevel(riskLevel)
                .confidence(confidence)
                .healthScore(healthScore)
                .modelVersion(modelVersion)
                .predictionStatus("COMPLETED")
                .featureImportanceJson(featureJson)
                .recommendationsJson(recommendationsJson)
                .triggeredBy(actor != null ? actor : "MANUAL")
                .build();

        prediction = predictionRepository.save(prediction);

        // Update repository with latest prediction result
        entity.setFailureProbability(failureProb);
        entity.setHealthScore(healthScore);
        entity.setRiskLevel(riskLevel);
        entity.setAiConfidence(confidence);
        entity.setPredictionStatus("COMPLETED");
        repoRepository.save(entity);

        syncService.logActivity(repositoryId, "AI_PREDICTION_RUN",
                "AI prediction completed — Failure probability: " + String.format("%.1f", failureProb * 100) + "%, Risk: " + riskLevel,
                actor, "PREDICTION", "INFO");

        log.info("Prediction completed for repository {} — risk={}, prob={}", repositoryId, riskLevel, failureProb);
        return prediction;
    }

    private double computeFailureProbability(RepositoryEntity entity) {
        double score = 0.0;

        // Factor: inactivity (last commit age)
        if (entity.getLastCommitDate() == null) score += 0.25;

        // Factor: open issues pressure
        int issues = entity.getOpenIssues() != null ? entity.getOpenIssues() : 0;
        score += Math.min(0.20, issues * 0.01);

        // Factor: no sync
        if (entity.getLastSyncDate() == null) score += 0.10;

        // Factor: lifecycle stage risk
        if ("DEPRECATED".equalsIgnoreCase(entity.getLifecycleStage())) score += 0.30;
        else if ("MAINTENANCE".equalsIgnoreCase(entity.getLifecycleStage())) score += 0.15;

        // Factor: low contributors
        int contributors = entity.getContributors() != null ? entity.getContributors() : 0;
        if (contributors <= 1) score += 0.15;
        else if (contributors <= 3) score += 0.05;

        return Math.min(0.99, Math.max(0.01, score));
    }

    private double computeConfidence(RepositoryEntity entity) {
        double base = 0.70;
        if (entity.getLastCommitDate() != null) base += 0.05;
        if (entity.getLastSyncDate() != null) base += 0.05;
        if (entity.getContributors() != null && entity.getContributors() > 0) base += 0.05;
        if (entity.getOpenIssues() != null) base += 0.05;
        return Math.min(0.98, base);
    }

    private String classifyRisk(double prob) {
        if (prob >= 0.75) return "CRITICAL";
        if (prob >= 0.50) return "HIGH";
        if (prob >= 0.25) return "MEDIUM";
        return "LOW";
    }

    private String buildFeatureImportanceJson(RepositoryEntity entity, double prob) {
        return String.format(
                "[{\"feature\":\"last_commit_age\",\"impact\":%.2f,\"direction\":\"increases_risk\"}," +
                "{\"feature\":\"open_issues\",\"impact\":%.2f,\"direction\":\"increases_risk\"}," +
                "{\"feature\":\"contributor_count\",\"impact\":%.2f,\"direction\":\"decreases_risk\"}," +
                "{\"feature\":\"lifecycle_stage\",\"impact\":%.2f,\"direction\":\"increases_risk\"}]",
                prob * 0.40, prob * 0.25, prob * 0.20, prob * 0.15
        );
    }

    private String buildRecommendationsJson(RepositoryEntity entity, String riskLevel) {
        if ("CRITICAL".equals(riskLevel)) {
            return "[\"Immediately assign dedicated maintainer\",\"Review and close stale issues\",\"Consider project deprecation plan\",\"Enable automated testing and CI/CD\"]";
        } else if ("HIGH".equals(riskLevel)) {
            return "[\"Increase commit frequency\",\"Add more contributors\",\"Enable background sync\",\"Schedule weekly prediction runs\"]";
        } else if ("MEDIUM".equals(riskLevel)) {
            return "[\"Monitor weekly\",\"Improve code coverage\",\"Reduce technical debt\",\"Enable notifications\"]";
        }
        return "[\"Maintain current development velocity\",\"Keep issue backlog manageable\",\"Regular code reviews recommended\"]";
    }
}
