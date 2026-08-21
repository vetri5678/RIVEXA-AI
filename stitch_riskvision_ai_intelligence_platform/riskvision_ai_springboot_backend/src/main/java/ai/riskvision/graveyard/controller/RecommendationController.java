package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.entity.RepositoryEntity;
import ai.riskvision.graveyard.entity.RepositoryMetricsEntity;
import ai.riskvision.graveyard.entity.RepositoryPredictionEntity;
import ai.riskvision.graveyard.repository.RepositoryEntityRepository;
import ai.riskvision.graveyard.repository.RepositoryMetricsEntityRepository;
import ai.riskvision.graveyard.repository.RepositoryPredictionEntityRepository;
import ai.riskvision.graveyard.service.RepoPredictionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ml")
@RequiredArgsConstructor
@Slf4j
public class RecommendationController {

    private final RepoPredictionService predictionService;
    private final RepositoryEntityRepository repoRepository;
    private final RepositoryMetricsEntityRepository metricsRepository;
    private final RepositoryPredictionEntityRepository predictionRepository;
    private final ObjectMapper objectMapper;

    @PostMapping("/recommendations/generate")
    public ResponseEntity<?> generateRecommendations(
            @RequestBody Map<String, String> body,
            Principal principal) {
        String repoIdStr = body != null ? body.get("repositoryId") : null;
        if (repoIdStr == null || repoIdStr.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "repositoryId is required"));
        }

        try {
            UUID repoId = UUID.fromString(repoIdStr);
            RepositoryEntity repository = repoRepository.findById(repoId)
                    .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repoId));

            RepositoryMetricsEntity metrics = metricsRepository.findByRepositoryId(repoId).orElse(null);
            
            // Get latest prediction to get riskScore, riskLevel, and topFactors
            RepositoryPredictionEntity latestPred = predictionRepository.findTopByRepositoryIdOrderByCreatedAtDesc(repoId)
                    .orElse(null);

            int riskScore = latestPred != null && latestPred.getRiskScore() != null ? latestPred.getRiskScore() : 50;
            String riskLevel = latestPred != null && latestPred.getRiskLevel() != null ? latestPred.getRiskLevel() : "MEDIUM";
            double failureProb = latestPred != null && latestPred.getFailureProbability() != null ? latestPred.getFailureProbability() : 0.5;
            String featureJson = latestPred != null ? latestPred.getFeatureImportanceJson() : "[]";

            String jsonResult;
            try {
                jsonResult = predictionService.generateRecommendationsWithAI(repository, metrics, riskScore, riskLevel, failureProb, featureJson);
            } catch (Exception ex) {
                log.warn("[RecommendationController] AI generation failed, executing local evidence-based rule engine. Error: {}", ex.getMessage());
                jsonResult = predictionService.generateFallbackRecommendations(repository, metrics, riskScore, riskLevel, failureProb, featureJson);
            }

            // Parse response
            if ("{}".equals(jsonResult) || jsonResult == null || jsonResult.isBlank()) {
                // If it fails completely, return a fallback matching schema
                jsonResult = "{\"recommendations\":[],\"roadmap\":{\"immediate\":[],\"short_term\":[],\"medium_term\":[]},\"projected_status\":{}}";
            }

            Map<?, ?> parsed = objectMapper.readValue(jsonResult, Map.class);
            return ResponseEntity.ok(parsed);

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            log.error("[RecommendationController] Generation error: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to generate recommendations: " + ex.getMessage()));
        }
    }
}
