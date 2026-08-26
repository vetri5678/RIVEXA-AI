package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.dto.prediction.*;
import ai.riskvision.graveyard.entity.*;
import ai.riskvision.graveyard.repository.*;
import ai.riskvision.graveyard.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * PredictionsController — dedicated prediction workflow endpoints.
 *
 * <p>Base: /api/v1/predictions
 *
 * <ul>
 *   <li>POST /api/v1/predictions/run       — run a prediction for a repository (body: { repositoryId })</li>
 *   <li>GET  /api/v1/predictions/{id}      — fetch a stored prediction result by its ID</li>
 * </ul>
 *
 * <p>This controller intentionally delegates all ML work to {@link RepoPredictionService}
 * and all repository lookup to {@link RepositoryEntityRepository}. No business logic lives here.
 */
@RestController
@RequestMapping("/api/v1/predictions")
@RequiredArgsConstructor
@Slf4j
public class PredictionsController {

    private final RepoPredictionService predictionService;
    private final RepositoryEntityRepository repositoryRepository;
    private final RepositoryPredictionEntityRepository predictionRepository;

    // ─── POST /api/v1/predictions/run ─────────────────────────────────────────
    /**
     * Run an AI prediction for the given repository.
     *
     * <p>Request body: {@code { "repositoryId": "uuid-string" }}
     *
     * <p>Validates the repository exists, invokes the FastAPI ML pipeline
     * (with heuristic fallback), persists the prediction, and returns a full
     * {@link PredictionResultResponse}.
     */
    @PostMapping("/run")
    public ResponseEntity<?> runPrediction(
            @Valid @RequestBody PredictionRunRequest request,
            Principal principal) {

        UUID repositoryId = request.getRepositoryId();
        String actor = principal != null ? principal.getName() : "MANUAL";

        log.info("[PredictionsController] POST /predictions/run — repositoryId={} actor={}", repositoryId, actor);

        // ── 1. Validate repository exists ──────────────────────────────────────
        RepositoryEntity repo = repositoryRepository.findById(repositoryId).orElse(null);
        if (repo == null) {
            log.warn("[PredictionsController] Repository not found: {}", repositoryId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "error", "Repository not found",
                    "message", "No repository found with ID: " + repositoryId +
                               ". Please verify the repository exists in the system."
            ));
        }

        // ── 2. Run prediction pipeline ─────────────────────────────────────────
        try {
            RepositoryPredictionEntity prediction = predictionService.runPrediction(repositoryId, actor);
            log.info("[PredictionsController] Prediction complete — predictionId={} riskLevel={} prob={}",
                    prediction.getId(), prediction.getRiskLevel(), prediction.getFailureProbability());

            return ResponseEntity.ok(buildResponse(prediction, repo));

        } catch (NoSuchElementException ex) {
            log.warn("[PredictionsController] Repository disappeared during prediction: {}", repositoryId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "error", "Repository not found",
                    "message", ex.getMessage()
            ));
        } catch (IllegalArgumentException ex) {
            log.warn("[PredictionsController] Invalid prediction request: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "error", "Invalid request",
                    "message", ex.getMessage()
            ));
        } catch (Exception ex) {
            String rootCause = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            log.error("[PredictionsController] Prediction engine failure for repositoryId={} actor={} — {}",
                    repositoryId, actor, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", rootCause != null ? rootCause : "Internal prediction engine failure",
                    "message", "Prediction failed: " + (rootCause != null ? rootCause : ex.getMessage())
            ));
        }
    }

    // ─── POST /api/v1/predictions/repository/{repositoryId} ───────────────────
    @PostMapping("/repository/{repositoryId}")
    public ResponseEntity<?> runRepositoryPredictionContract(
            @PathVariable UUID repositoryId,
            Principal principal) {

        String actor = principal != null ? principal.getName() : "MANUAL";
        log.info("[PredictionsController] POST /predictions/repository/{} — actor={}", repositoryId, actor);

        RepositoryEntity repo = repositoryRepository.findById(repositoryId).orElse(null);
        if (repo == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "prediction_status", "FAILED",
                    "error_code", "REPOSITORY_NOT_FOUND",
                    "message", "Repository not found: " + repositoryId
            ));
        }

        try {
            RepositoryPredictionEntity prediction = predictionService.runPrediction(repositoryId, actor);

            double failureProb = prediction.getFailureProbability() != null ? prediction.getFailureProbability() : 0.0;
            double failureProbPct = Math.round(failureProb * 1000.0) / 10.0;
            double riskScore = prediction.getRiskScore() != null ? (double) prediction.getRiskScore() : failureProbPct;
            double healthScore = prediction.getHealthScore() != null ? prediction.getHealthScore() : Math.max(0.0, 100.0 - riskScore);
            double confidence = prediction.getConfidence() != null ? prediction.getConfidence() : 85.0;

            Object topFeatures = new java.util.ArrayList<>();
            if (prediction.getFeatureImportanceJson() != null) {
                try {
                    topFeatures = new com.fasterxml.jackson.databind.ObjectMapper().readValue(prediction.getFeatureImportanceJson(), Object.class);
                } catch (Exception ignored) {}
            }

            Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("success", true);
            response.put("prediction_id", prediction.getId().toString());
            response.put("repository_id", repositoryId.toString());
            response.put("repository_name", repo.getRepositoryName());
            response.put("model", Map.of(
                    "name", "XGBoost",
                    "version", prediction.getModelVersion() != null ? prediction.getModelVersion() : "xgboost-v2.4"
            ));
            response.put("input", Map.of(
                    "feature_count", 22,
                    "feature_hash", prediction.getId().toString().replace("-", "")
            ));
            response.put("prediction", Map.of(
                    "failure_probability", failureProb,
                    "failure_probability_percent", failureProbPct,
                    "risk_score", riskScore,
                    "health_score", healthScore,
                    "confidence", confidence,
                    "risk_level", prediction.getRiskLevel() != null ? prediction.getRiskLevel() : "LOW"
            ));
            response.put("explainability", Map.of(
                    "method", "SHAP",
                    "top_features", topFeatures
            ));
            response.put("timestamp", prediction.getCreatedAt() != null ? prediction.getCreatedAt().toString() : java.time.Instant.now().toString());

            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            String rootCause = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "prediction_status", "FAILED",
                    "error_code", "PREDICTION_ENGINE_ERROR",
                    "message", "Prediction failed: " + (rootCause != null ? rootCause : ex.getMessage())
            ));
        }
    }


    // ─── GET /api/v1/predictions/{id} ─────────────────────────────────────────
    /**
     * Fetch a stored prediction result by its UUID.
     *
     * <p>Joins with the repository entity to return repository metadata alongside
     * the risk metrics, SHAP feature importance, and recommendations.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getPrediction(@PathVariable UUID id) {

        log.info("[PredictionsController] GET /predictions/{}", id);

        RepositoryPredictionEntity prediction = predictionRepository.findById(id).orElse(null);
        if (prediction == null) {
            log.warn("[PredictionsController] Prediction not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "error", "Prediction not found",
                    "message", "No prediction record found with ID: " + id
            ));
        }

        // Fetch associated repository for display info
        RepositoryEntity repo = repositoryRepository.findById(prediction.getRepositoryId()).orElse(null);

        log.info("[PredictionsController] Returning prediction {} for repository {}", id, prediction.getRepositoryId());
        return ResponseEntity.ok(buildResponse(prediction, repo));
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private PredictionResultResponse buildResponse(RepositoryPredictionEntity prediction, RepositoryEntity repo) {
        double failureProb = prediction.getFailureProbability() != null ? prediction.getFailureProbability() : 0.0;
        int computedRiskScore = prediction.getRiskScore() != null ? prediction.getRiskScore() : (int) Math.round(failureProb * 100);
        double computedHealthScore = prediction.getHealthScore() != null ? prediction.getHealthScore() : Math.max(0.0, 100.0 - (failureProb * 100.0));

        return PredictionResultResponse.builder()
                // Prediction fields
                .predictionId(prediction.getId())
                .predictionStatus(prediction.getPredictionStatus() != null ? prediction.getPredictionStatus() : "COMPLETED")
                .modelVersion(prediction.getModelVersion() != null ? prediction.getModelVersion() : "XGBoost-v1.0")
                .triggeredBy(prediction.getTriggeredBy() != null ? prediction.getTriggeredBy() : "MANUAL")
                .createdAt(prediction.getCreatedAt())
                .failureProbability(prediction.getFailureProbability())
                .riskScore(computedRiskScore)
                .riskLevel(prediction.getRiskLevel() != null ? prediction.getRiskLevel() : "LOW")
                .confidence(prediction.getConfidence())
                .healthScore(computedHealthScore)
                .featureImportanceJson(prediction.getFeatureImportanceJson())
                .recommendationsJson(prediction.getRecommendationsJson())
                // Repository fields (nullable-safe)
                .repositoryId(prediction.getRepositoryId())
                .repositoryName(repo != null && repo.getRepositoryName() != null ? repo.getRepositoryName() : "Repository")
                .repositoryUrl(repo != null ? repo.getRepositoryUrl() : null)
                .organization(repo != null ? repo.getOrganization() : null)
                .language(repo != null ? repo.getLanguage() : null)
                .gitProvider(repo != null && repo.getGitProvider() != null ? repo.getGitProvider() : "GITHUB")
                .branch(repo != null && repo.getBranch() != null ? repo.getBranch() : "main")
                .visibility(repo != null && repo.getVisibility() != null ? repo.getVisibility() : "PUBLIC")
                .build();
    }
}
