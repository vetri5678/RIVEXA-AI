package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.dto.prediction.PredictionRequestDTO;
import ai.riskvision.graveyard.dto.prediction.PredictionResponseDTO;
import ai.riskvision.graveyard.entity.PredictionHistoryEntity;
import ai.riskvision.graveyard.service.PredictionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * PredictionController — REST endpoints exposing the ML Prediction Module.
 * All endpoints proxy to FastAPI ML service via PredictionService/PredictionClient.
 *
 * Base: POST /api/v1/ml/predict
 *       GET  /api/v1/ml/metrics
 *       GET  /api/v1/ml/model
 *       GET  /api/v1/ml/health
 *       GET  /api/v1/ml/version
 *       GET  /api/v1/ml/feature-importance
 *       GET  /api/v1/ml/analytics
 *       GET  /api/v1/ml/history
 *       GET  /api/v1/ml/history/{projectId}
 */
@RestController
@RequestMapping("/api/v1/ml")
@RequiredArgsConstructor
@Slf4j
public class PredictionController {

    private final PredictionService predictionService;

    // ─── POST /api/v1/ml/predict ──────────────────────────────────────────────
    @PostMapping("/predict")
    public ResponseEntity<PredictionResponseDTO> predict(
            @Valid @RequestBody PredictionRequestDTO request,
            @RequestParam(value = "projectId", required = false) String projectId,
            Principal principal) {

        String actor = principal != null ? principal.getName() : "API";
        log.info("[PredictionController] Received prediction request for projectId={} actor={}", projectId, actor);

        PredictionResponseDTO response = predictionService.runPrediction(request, projectId, actor);
        return ResponseEntity.ok(response);
    }

    // ─── GET /api/v1/ml/metrics ───────────────────────────────────────────────
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        return ResponseEntity.ok(predictionService.getMetrics());
    }

    // ─── GET /api/v1/ml/model ─────────────────────────────────────────────────
    @GetMapping("/model")
    public ResponseEntity<Map<String, Object>> getModel() {
        return ResponseEntity.ok(predictionService.getModelInfo());
    }

    // ─── GET /api/v1/ml/health ────────────────────────────────────────────────
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        return ResponseEntity.ok(predictionService.getHealth());
    }

    // ─── GET /api/v1/ml/version ───────────────────────────────────────────────
    @GetMapping("/version")
    public ResponseEntity<Map<String, Object>> getVersion() {
        return ResponseEntity.ok(predictionService.getVersion());
    }

    // ─── GET /api/v1/ml/feature-importance ───────────────────────────────────
    @GetMapping("/feature-importance")
    public ResponseEntity<Map<String, Object>> getFeatureImportance() {
        return ResponseEntity.ok(predictionService.getFeatureImportance());
    }

    // ─── GET /api/v1/ml/analytics ─────────────────────────────────────────────
    @GetMapping("/analytics")
    public ResponseEntity<Map<String, Object>> getAnalytics() {
        return ResponseEntity.ok(predictionService.getAnalyticsSummary());
    }

    // ─── GET /api/v1/ml/history ───────────────────────────────────────────────
    @GetMapping("/history")
    public ResponseEntity<List<PredictionHistoryEntity>> getHistory() {
        return ResponseEntity.ok(predictionService.getRecentHistory());
    }

    // ─── GET /api/v1/ml/history/{projectId} ───────────────────────────────────
    @GetMapping("/history/{projectId}")
    public ResponseEntity<List<PredictionHistoryEntity>> getHistoryForProject(
            @PathVariable String projectId) {
        return ResponseEntity.ok(predictionService.getHistoryForProject(projectId));
    }

    // ─── GET /api/v1/ml/risk-distribution ─────────────────────────────────────
    @GetMapping("/risk-distribution")
    public ResponseEntity<Map<String, Object>> getRiskDistribution() {
        return ResponseEntity.ok(predictionService.getRiskDistribution());
    }
}
