package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.dto.repository.*;
import ai.riskvision.graveyard.entity.RepositoryPredictionEntity;
import ai.riskvision.graveyard.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/repositories")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class RepositoryController {

    private final RepositoryService repositoryService;
    private final RepositoryAnalyticsService analyticsService;
    private final RepositorySyncService syncService;
    private final RepoPredictionService predictionService;
    private final RepositoryValidationService validationService;

    // ─── GET /api/v1/repositories ─────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<PagedRepositoryResponse> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String predictionStatus,
            @RequestParam(required = false) String gitProvider,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String organization) {

        PagedRepositoryResponse response = repositoryService.findAll(
                page, size, sortBy, sortDir,
                search, status, riskLevel, predictionStatus, gitProvider, language, organization
        );
        return ResponseEntity.ok(response);
    }

    // ─── GET /api/v1/repositories/statistics ──────────────────────────────────
    @GetMapping("/statistics")
    public ResponseEntity<RepositoryStatisticsResponse> getStatistics() {
        return ResponseEntity.ok(analyticsService.computeStatistics());
    }

    // ─── GET /api/v1/repositories/{id} ────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<RepositoryDetailResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(repositoryService.findById(id));
    }

    // ─── POST /api/v1/repositories ────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<RepositoryResponse> create(
            @Valid @RequestBody RepositoryCreateRequest request,
            Principal principal) {
        String actor = principal != null ? principal.getName() : "API";
        RepositoryResponse response = repositoryService.create(request, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ─── PUT /api/v1/repositories/{id} ────────────────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<RepositoryResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody RepositoryUpdateRequest request,
            Principal principal) {
        String actor = principal != null ? principal.getName() : "API";
        return ResponseEntity.ok(repositoryService.update(id, request, actor));
    }

    // ─── DELETE /api/v1/repositories/{id} ─────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Principal principal) {
        String actor = principal != null ? principal.getName() : "API";
        repositoryService.delete(id, actor);
        return ResponseEntity.noContent().build();
    }

    // ─── PATCH /api/v1/repositories/{id}/archive ──────────────────────────────
    @PatchMapping("/{id}/archive")
    public ResponseEntity<RepositoryResponse> archive(@PathVariable UUID id, Principal principal) {
        String actor = principal != null ? principal.getName() : "API";
        return ResponseEntity.ok(repositoryService.archive(id, actor));
    }

    // ─── PATCH /api/v1/repositories/{id}/restore ──────────────────────────────
    @PatchMapping("/{id}/restore")
    public ResponseEntity<RepositoryResponse> restore(@PathVariable UUID id, Principal principal) {
        String actor = principal != null ? principal.getName() : "API";
        return ResponseEntity.ok(repositoryService.restore(id, actor));
    }

    // ─── POST /api/v1/repositories/{id}/duplicate ─────────────────────────────
    @PostMapping("/{id}/duplicate")
    public ResponseEntity<RepositoryResponse> duplicate(@PathVariable UUID id, Principal principal) {
        String actor = principal != null ? principal.getName() : "API";
        return ResponseEntity.status(HttpStatus.CREATED).body(repositoryService.duplicate(id, actor));
    }

    // ─── POST /api/v1/repositories/{id}/sync ──────────────────────────────────
    @PostMapping("/{id}/sync")
    public ResponseEntity<Map<String, Object>> sync(@PathVariable UUID id, Principal principal) {
        String actor = principal != null ? principal.getName() : "SYSTEM";
        syncService.syncRepository(id, actor);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Repository synchronization initiated",
                "repositoryId", id.toString()
        ));
    }

    // ─── POST /api/v1/repositories/{id}/predict ───────────────────────────────
    @PostMapping("/{id}/predict")
    public ResponseEntity<Map<String, Object>> predict(@PathVariable UUID id, Principal principal) {
        String actor = principal != null ? principal.getName() : "MANUAL";
        RepositoryPredictionEntity result = predictionService.runPrediction(id, actor);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "AI prediction completed",
                "predictionId", result.getId().toString(),
                "failureProbability", result.getFailureProbability(),
                "riskLevel", result.getRiskLevel(),
                "confidence", result.getConfidence(),
                "healthScore", result.getHealthScore()
        ));
    }

    // ─── GET /api/v1/repositories/{id}/metrics ────────────────────────────────
    @GetMapping("/{id}/metrics")
    public ResponseEntity<RepositoryMetricsResponse> getMetrics(@PathVariable UUID id) {
        return ResponseEntity.ok(repositoryService.getMetrics(id));
    }

    // ─── GET /api/v1/repositories/{id}/history ────────────────────────────────
    @GetMapping("/{id}/history")
    public ResponseEntity<RepositoryDetailResponse> getHistory(@PathVariable UUID id) {
        // Returns detail with predictionHistory and recentActivities populated
        return ResponseEntity.ok(repositoryService.findById(id));
    }

    // ─── GET /api/v1/repositories/export ──────────────────────────────────────
    @GetMapping("/export")
    public ResponseEntity<PagedRepositoryResponse> export(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "1000") int size) {
        // Returns a large page for client-side export
        PagedRepositoryResponse response = repositoryService.findAll(
                page, size, "createdAt", "desc",
                null, status, riskLevel, null, null, null, null
        );
        return ResponseEntity.ok(response);
    }

    // ─── POST /api/v1/repositories/validate-token ─────────────────────────────
    @PostMapping("/validate-token")
    public ResponseEntity<Map<String, Object>> validateToken(
            @RequestBody Map<String, String> payload) {
        String provider = payload.get("gitProvider");
        String token = payload.get("token");
        String url = payload.get("repositoryUrl");
        boolean valid = validationService.validateConnectionToken(provider, token, url);
        return ResponseEntity.ok(Map.of(
                "valid", valid,
                "message", valid ? "Token appears valid" : "Token is invalid or too short"
        ));
    }

    // ─── Global error handling ─────────────────────────────────────────────────
    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(java.util.NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleValidation(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }
}
