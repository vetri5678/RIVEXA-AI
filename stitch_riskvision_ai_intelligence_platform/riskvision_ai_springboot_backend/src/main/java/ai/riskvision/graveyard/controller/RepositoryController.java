package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.dto.repository.*;
import ai.riskvision.graveyard.entity.RepositoryPredictionEntity;
import ai.riskvision.graveyard.service.*;
import ai.riskvision.graveyard.util.GitHubUrlParser;
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
        log.info("[RepositoryController] POST /repositories/{}/predict — actor={}", id, actor);
        try {
            RepositoryPredictionEntity result = predictionService.runPrediction(id, actor);
            log.info("[RepositoryController] Prediction succeeded for repositoryId={} riskLevel={} failureProb={}",
                    id, result.getRiskLevel(), result.getFailureProbability());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "AI prediction completed",
                    "predictionId", result.getId().toString(),
                    "failureProbability", result.getFailureProbability(),
                    "riskLevel", result.getRiskLevel(),
                    "confidence", result.getConfidence(),
                    "healthScore", result.getHealthScore()
            ));
        } catch (java.util.NoSuchElementException ex) {
            log.warn("[RepositoryController] Repository not found for prediction: repositoryId={} — {}", id, ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "error", "Repository not found: " + id,
                    "message", "Repository not found. Verify the UUID exists in the system."
            ));
        } catch (IllegalArgumentException ex) {
            log.warn("[RepositoryController] Invalid parameters for prediction: repositoryId={} — {}", id, ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "error", ex.getMessage(),
                    "message", "Invalid repository parameters: " + ex.getMessage()
            ));
        } catch (Exception ex) {
            log.error("[RepositoryController] Prediction engine failure for repositoryId={} actor={} — {}",
                    id, actor, ex.getMessage(), ex);
            String rootCause = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", rootCause != null ? rootCause : "Internal prediction engine failure",
                    "message", "Prediction failed: " + (rootCause != null ? rootCause : ex.getMessage())
            ));
        }
    }

    // ─── POST /api/v1/repositories/predict-by-url ─────────────────────────────
    /**
     * GitHub-native prediction entry point.
     *
     * <p>Accepts a raw GitHub repository URL. The backend will:
     * <ol>
     *   <li>Parse the URL to extract owner/repo.</li>
     *   <li>Look up or create a {@code RepositoryEntity} (fetching live metadata via GitHub API).</li>
     *   <li>Run the full ML prediction pipeline.</li>
     *   <li>Return prediction results including the resolved repository ID and name.</li>
     * </ol>
     *
     * <p>Request body: {@code { "githubUrl": "https://github.com/owner/repo" }}
     */
    @PostMapping("/predict-by-url")
    public ResponseEntity<Map<String, Object>> predictByUrl(
            @RequestBody Map<String, String> body,
            Principal principal) {

        String actor = principal != null ? principal.getName() : "API";
        String githubUrl = body != null ? body.get("githubUrl") : null;

        log.info("[RepositoryController] POST /repositories/predict-by-url — actor={} url={}", actor, githubUrl);

        // ── 1. Validate URL ────────────────────────────────────────────────────
        if (githubUrl == null || githubUrl.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "error", "githubUrl is required",
                    "message", "Request body must include a 'githubUrl' field. Example: https://github.com/owner/repo"
            ));
        }

        if (!GitHubUrlParser.isValidGitHubUrl(githubUrl)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "error", "Invalid GitHub URL: " + githubUrl,
                    "message", "The URL must be a valid GitHub repository URL. Example: https://github.com/owner/repo"
            ));
        }

        try {
            // ── 2. Resolve or create the repository entity ─────────────────────
            ai.riskvision.graveyard.entity.RepositoryEntity entity =
                    repositoryService.findOrCreateByGithubUrl(githubUrl, actor);
            UUID repositoryId = entity.getId();

            log.info("[RepositoryController] Resolved repositoryId={} for url={}", repositoryId, githubUrl);

            // ── 3. Run prediction ──────────────────────────────────────────────
            RepositoryPredictionEntity result = predictionService.runPrediction(repositoryId, actor);

            log.info("[RepositoryController] Prediction-by-url succeeded — repositoryId={} riskLevel={} url={}",
                    repositoryId, result.getRiskLevel(), githubUrl);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "AI prediction completed for GitHub repository",
                    "repositoryId", repositoryId.toString(),
                    "repositoryName", entity.getRepositoryName(),
                    "repositoryUrl", entity.getRepositoryUrl(),
                    "predictionId", result.getId().toString(),
                    "failureProbability", result.getFailureProbability(),
                    "riskLevel", result.getRiskLevel(),
                    "confidence", result.getConfidence(),
                    "healthScore", result.getHealthScore()
            ));

        } catch (IllegalArgumentException ex) {
            log.warn("[RepositoryController] predict-by-url validation error — url={} error={}", githubUrl, ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "error", ex.getMessage(),
                    "message", ex.getMessage()
            ));
        } catch (ai.riskvision.graveyard.exception.GitHubResourceNotFoundException ex) {
            log.warn("[RepositoryController] predict-by-url — GitHub repo not found: {}", githubUrl);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "error", "GitHub repository not found",
                    "message", "The repository at '" + githubUrl + "' does not exist or is private. " +
                               "Ensure the repository is public or a valid GitHub PAT is configured."
            ));
        } catch (ai.riskvision.graveyard.exception.GitHubAuthenticationException ex) {
            log.warn("[RepositoryController] predict-by-url — GitHub auth failed: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "error", "GitHub authentication failed",
                    "message", "The GitHub Personal Access Token (PAT) is invalid or not configured. " +
                               "Set GITHUB_TOKEN environment variable."
            ));
        } catch (ai.riskvision.graveyard.exception.GitHubRateLimitException ex) {
            log.warn("[RepositoryController] predict-by-url — GitHub rate limit exceeded: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "success", false,
                    "error", "GitHub API rate limit exceeded",
                    "message", "Too many requests to GitHub API. Please wait a few minutes and try again."
            ));
        } catch (Exception ex) {
            log.error("[RepositoryController] predict-by-url failed — url={} actor={} error={}", githubUrl, actor, ex.getMessage(), ex);
            String rootCause = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", rootCause != null ? rootCause : "Internal server error",
                    "message", "Prediction failed: " + (rootCause != null ? rootCause : ex.getMessage())
            ));
        }
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
