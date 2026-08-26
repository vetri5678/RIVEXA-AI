package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.client.PredictionClient;
import ai.riskvision.graveyard.dto.prediction.PredictionRequestDTO;
import ai.riskvision.graveyard.dto.prediction.PredictionResponseDTO;
import ai.riskvision.graveyard.entity.PredictionHistoryEntity;
import ai.riskvision.graveyard.repository.PredictionHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * PredictionService — orchestrates prediction requests to the FastAPI ML service,
 * persists results to prediction_history, and computes analytics.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PredictionService {

    private final PredictionClient predictionClient;
    private final PredictionHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;
    private final N8nWebhookService n8nWebhookService;

    // Report counter automatically incremented on prediction reports
    private final AtomicLong reportCounter = new AtomicLong(12L);

    /**
     * Run a prediction via FastAPI ML service and persist to database.
     */
    @Transactional
    public PredictionResponseDTO runPrediction(PredictionRequestDTO request, String projectId, String createdBy) {
        log.info("[PredictionService] Running ML prediction for projectId={} actor={}", projectId, createdBy);

        PredictionResponseDTO response = predictionClient.predict(request);

        if (response != null) {
            persistHistory(response, projectId, createdBy);
            reportCounter.incrementAndGet();
            log.info("[PredictionService] Prediction complete: id={} riskLevel={} confidence={} reportCount={}",
                    response.getId(), response.getRiskLevel(), response.getConfidence(), reportCounter.get());

            // Trigger non-blocking external n8n webhook notification
            try {
                n8nWebhookService.triggerPredictionCompletedWebhook(
                        response.getId(),
                        projectId,
                        response.getRiskLevel(),
                        response.getProbability() != null ? response.getProbability() : 0.0,
                        response.getConfidence() != null ? response.getConfidence() : 0.0,
                        createdBy
                );

                double score = response.getRiskScore() != null ? response.getRiskScore() : (response.getProbability() != null ? response.getProbability() * 100.0 : 0.0);
                String riskLevel = response.getRiskLevel() != null ? response.getRiskLevel().toUpperCase() : "UNKNOWN";
                if ("HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel) || score >= 80.0) {
                    n8nWebhookService.triggerHighRiskDetectedWebhook(
                            response.getId(),
                            projectId,
                            projectId,
                            riskLevel,
                            score,
                            response.getProbability() != null ? response.getProbability() : 0.0,
                            createdBy
                    );
                }
            } catch (Exception e) {
                log.warn("[PredictionService] Non-critical error triggering prediction/high-risk webhooks: {}", e.getMessage());
            }
        }

        return response;
    }

    /**
     * Persist a completed prediction to the prediction_history table.
     */
    @Transactional
    public void persistHistory(PredictionResponseDTO response, String projectId, String createdBy) {
        try {
            String topFactorsJson = objectMapper.writeValueAsString(response.getTopFactors());
            String predictionJson = objectMapper.writeValueAsString(response);

            PredictionHistoryEntity entity = PredictionHistoryEntity.builder()
                    .id(response.getId() != null ? response.getId() : UUID.randomUUID().toString())
                    .repositoryId(projectId) // Map to repository_id / project_id
                    .projectId(projectId)
                    .riskScore(response.getRiskScore() != null ? response.getRiskScore() : 0.0)
                    .riskLevel(response.getRiskLevel() != null ? response.getRiskLevel() : "UNKNOWN")
                    .confidence(response.getConfidence() != null ? response.getConfidence() : 0.0)
                    .probability(response.getProbability() != null ? response.getProbability() : 0.0)
                    .topFactors(topFactorsJson)
                    .predictionJson(predictionJson)
                    .modelVersion(response.getModelVersion() != null ? response.getModelVersion() : "1.0.0")
                    .createdBy(createdBy != null ? createdBy : "SYSTEM")
                    .build();

            historyRepository.save(entity);
            log.debug("[PredictionService] Persisted prediction {} to database", entity.getId());
        } catch (Exception e) {
            log.error("[PredictionService] Failed to persist prediction history: {}", e.getMessage(), e);
        }
    }

    /**
     * Retrieve risk distribution analytics from database.
     */
    public Map<String, Object> getRiskDistribution() {
        List<Object[]> counts = historyRepository.countByRiskLevelGrouped();
        Map<String, Long> distribution = new LinkedHashMap<>();
        long total = 0L;
        for (Object[] row : counts) {
            String level = (String) row[0];
            Long count = (Long) row[1];
            distribution.put(level, count);
            total += count;
        }
        return Map.of(
                "distribution", distribution,
                "total_predictions", total,
                "reports_generated", reportCounter.get(),
                "avg_confidence", Optional.ofNullable(historyRepository.findAverageConfidence()).orElse(0.0),
                "avg_risk_score", Optional.ofNullable(historyRepository.findAverageRiskScore()).orElse(0.0)
        );
    }

    /**
     * Retrieve recent prediction history (latest 50 records).
     */
    public List<PredictionHistoryEntity> getRecentHistory() {
        return historyRepository.findTop50ByOrderByCreatedAtDesc();
    }

    /**
     * Retrieve predictions for a specific project.
     */
    public List<PredictionHistoryEntity> getHistoryForProject(String projectId) {
        return historyRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    /**
     * Retrieve full ML model metadata via FastAPI.
     */
    public Map<String, Object> getModelInfo() {
        return predictionClient.getModelInfo();
    }

    /**
     * Retrieve evaluation metrics via FastAPI.
     */
    public Map<String, Object> getMetrics() {
        return predictionClient.getMetrics();
    }

    /**
     * Retrieve feature importance via FastAPI.
     */
    public Map<String, Object> getFeatureImportance() {
        return predictionClient.getFeatureImportance();
    }

    /**
     * Retrieve ML service health status.
     */
    public Map<String, Object> getHealth() {
        return predictionClient.getHealth();
    }

    /**
     * Retrieve ML service version.
     */
    public Map<String, Object> getVersion() {
        return predictionClient.getVersion();
    }

    /**
     * Summary analytics for dashboard widgets.
     */
    public Map<String, Object> getAnalyticsSummary() {
        long totalPredictions = historyRepository.count();
        long highRisk = historyRepository.countByRiskLevel("HIGH");
        long mediumRisk = historyRepository.countByRiskLevel("MEDIUM");
        long lowRisk = historyRepository.countByRiskLevel("LOW");
        double avgConfidence = Optional.ofNullable(historyRepository.findAverageConfidence()).orElse(0.0);
        double avgRiskScore  = Optional.ofNullable(historyRepository.findAverageRiskScore()).orElse(0.0);

        ZonedDateTime todayStart = ZonedDateTime.now().toLocalDate().atStartOfDay(ZonedDateTime.now().getZone());
        long todayCount = historyRepository.countByCreatedAtBetween(todayStart, ZonedDateTime.now());

        Map<String, Object> mlMetrics = getMetrics();
        Map<String, Object> modelInfo  = getModelInfo();

        return Map.of(
                "total_predictions", totalPredictions,
                "reports_generated", reportCounter.get(),
                "high_risk_count", highRisk,
                "medium_risk_count", mediumRisk,
                "low_risk_count", lowRisk,
                "today_predictions", todayCount,
                "avg_confidence", Math.round(avgConfidence * 10.0) / 10.0,
                "avg_risk_score", Math.round(avgRiskScore * 10.0) / 10.0,
                "model_metrics", mlMetrics,
                "model_info", modelInfo
        );
    }

    public long getReportCount() {
        return reportCounter.get();
    }
}
