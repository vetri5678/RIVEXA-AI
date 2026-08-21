package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.dto.pipeline.PipelineStatusResponse;
import ai.riskvision.graveyard.service.PipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

import ai.riskvision.graveyard.service.DashboardService;

@RestController
@RequestMapping("/api/v1/pipeline")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class PipelineController {

    private final PipelineService pipelineService;
    private final DashboardService dashboardService;

    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        long startTime = System.currentTimeMillis();
        log.debug("HTTP GET /api/v1/pipeline/status received");
        try {
            PipelineStatusResponse status = pipelineService.getPipelineStatus();
            long duration = System.currentTimeMillis() - startTime;
            log.info("HTTP GET /api/v1/pipeline/status completed in {} ms with status 200 OK", duration);
            return ResponseEntity.ok(status);
        } catch (Exception ex) {
            log.error("Failed to process /api/v1/pipeline/status: {}", ex.getMessage(), ex);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Pipeline Status Failure",
                    "detail", ex.getMessage(),
                    "timestamp", LocalDateTime.now()
            ));
        }
    }

    @GetMapping("/metrics")
    public ResponseEntity<?> getMetrics() {
        log.debug("HTTP GET /api/v1/pipeline/metrics received");
        PipelineStatusResponse status = pipelineService.getPipelineStatus();
        return ResponseEntity.ok(status.getMetrics());
    }

    @GetMapping("/lifecycle")
    public ResponseEntity<?> getLifecycle() {
        log.debug("HTTP GET /api/v1/pipeline/lifecycle received");
        PipelineStatusResponse status = pipelineService.getPipelineStatus();
        return ResponseEntity.ok(Map.of(
                "status", status.getStatus(),
                "active_stage", status.getActiveStage(),
                "model_version", status.getModelVersion(),
                "timestamp", status.getTimestamp(),
                "stages", status.getStages()
        ));
    }

    @GetMapping("/evaluation")
    public ResponseEntity<?> getEvaluation() {
        log.debug("HTTP GET /api/v1/pipeline/evaluation received");
        Map<String, Object> modelInfo = dashboardService.getModelInfo();
        return ResponseEntity.ok(Map.of(
                "f1_score", modelInfo.getOrDefault("f1_score", "Unavailable"),
                "accuracy", modelInfo.getOrDefault("accuracy", "Unavailable"),
                "precision", modelInfo.getOrDefault("precision", "Unavailable"),
                "recall", modelInfo.getOrDefault("recall", "Unavailable"),
                "roc_auc", modelInfo.getOrDefault("roc_auc", "Unavailable"),
                "evaluation_date", modelInfo.getOrDefault("training_date", LocalDateTime.now().toString())
        ));
    }

    @GetMapping("/repository-sync")
    public ResponseEntity<?> getRepositorySync() {
        log.debug("HTTP GET /api/v1/pipeline/repository-sync received");
        return ResponseEntity.ok(pipelineService.getRepositorySyncData());
    }

    @GetMapping("/extract")
    public ResponseEntity<?> getExtract() {
        log.debug("HTTP GET /api/v1/pipeline/extract received");
        return ResponseEntity.ok(pipelineService.getExtractionData());
    }

    @GetMapping("/cleanse")
    public ResponseEntity<?> getCleanse() {
        log.debug("HTTP GET /api/v1/pipeline/cleanse received");
        return ResponseEntity.ok(pipelineService.getCleansingData());
    }

    @GetMapping({"/model", "/model-engine"})
    public ResponseEntity<?> getModel() {
        log.debug("HTTP GET /api/v1/pipeline/model received");
        return ResponseEntity.ok(pipelineService.getModelEngineData());
    }

    @GetMapping("/inference")
    public ResponseEntity<?> getInference() {
        log.debug("HTTP GET /api/v1/pipeline/inference received");
        return ResponseEntity.ok(pipelineService.getInferenceData());
    }

    @GetMapping("/shap")
    public ResponseEntity<?> getShap() {
        log.debug("HTTP GET /api/v1/pipeline/shap received");
        return ResponseEntity.ok(pipelineService.getShapXaiData());
    }
}
