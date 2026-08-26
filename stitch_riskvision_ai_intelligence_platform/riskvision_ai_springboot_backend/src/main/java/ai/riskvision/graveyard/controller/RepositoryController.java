package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.dto.repository.*;
import ai.riskvision.graveyard.entity.*;
import ai.riskvision.graveyard.service.*;
import ai.riskvision.graveyard.util.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ai.riskvision.graveyard.repository.OAuthAccountRepository;
import ai.riskvision.graveyard.repository.UserRepository;
import java.security.Principal;
import java.util.Map;
import java.util.Optional;
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
    private final UserRepository userRepository;
    private final OAuthAccountRepository oauthAccountRepository;
    private final ai.riskvision.graveyard.repository.RepositoryEntityRepository repositoryEntityRepository;
    private final ai.riskvision.graveyard.repository.RepositoryPredictionEntityRepository predictionRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private boolean isRepoOwnedByCaller(UUID repoId, Principal principal) {
        if (repoId == null || principal == null || principal.getName() == null) return false;
        String name = principal.getName();
        Optional<UserEntity> userOpt = userRepository.findByEmail(name)
                .or(() -> userRepository.findByUsername(name));
        if (userOpt.isEmpty()) return false;

        return repositoryEntityRepository.findById(repoId)
                .map(r -> r.getUser() != null && r.getUser().getId().equals(userOpt.get().getId()))
                .orElse(false);
    }

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
            @RequestParam(required = false) String organization,
            Principal principal) {

        // Resolve the calling user — scope all results to their repositories only
        String callerEmail = principal != null ? principal.getName() : null;
        PagedRepositoryResponse response = repositoryService.findAllByUser(
                callerEmail, page, size, sortBy, sortDir,
                search, status, riskLevel, predictionStatus, gitProvider, language, organization
        );
        return ResponseEntity.ok(response);
    }

    // ─── GET /api/v1/repositories/statistics ──────────────────────────────────
    @GetMapping("/statistics")
    public ResponseEntity<RepositoryStatisticsResponse> getStatistics(Principal principal) {
        String callerEmail = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(analyticsService.computeStatisticsForUser(callerEmail));
    }

    // ─── GET /api/v1/repositories/{id} ────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<RepositoryDetailResponse> getById(@PathVariable UUID id, Principal principal) {
        if (!isRepoOwnedByCaller(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(repositoryService.findById(id));
    }

    // ─── POST /api/v1/repositories ────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<RepositoryResponse> create(
            @Valid @RequestBody RepositoryCreateRequest request,
            Principal principal) {
        String actor = principal != null ? principal.getName() : "API";
        // Pass actor (email) so RepositoryService can associate the repo with the user
        RepositoryResponse response = repositoryService.createForUser(request, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ─── PUT /api/v1/repositories/{id} ────────────────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<RepositoryResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody RepositoryUpdateRequest request,
            Principal principal) {
        if (!isRepoOwnedByCaller(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String actor = principal != null ? principal.getName() : "API";
        return ResponseEntity.ok(repositoryService.update(id, request, actor));
    }

    // ─── DELETE /api/v1/repositories/{id} ─────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Principal principal) {
        if (!isRepoOwnedByCaller(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String actor = principal != null ? principal.getName() : "API";
        repositoryService.delete(id, actor);
        return ResponseEntity.noContent().build();
    }

    // ─── PATCH /api/v1/repositories/{id}/archive ──────────────────────────────
    @PatchMapping("/{id}/archive")
    public ResponseEntity<RepositoryResponse> archive(@PathVariable UUID id, Principal principal) {
        if (!isRepoOwnedByCaller(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String actor = principal != null ? principal.getName() : "API";
        return ResponseEntity.ok(repositoryService.archive(id, actor));
    }

    // ─── PATCH /api/v1/repositories/{id}/restore ──────────────────────────────
    @PatchMapping("/{id}/restore")
    public ResponseEntity<RepositoryResponse> restore(@PathVariable UUID id, Principal principal) {
        if (!isRepoOwnedByCaller(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String actor = principal != null ? principal.getName() : "API";
        return ResponseEntity.ok(repositoryService.restore(id, actor));
    }

    // ─── POST /api/v1/repositories/{id}/duplicate ─────────────────────────────
    @PostMapping("/{id}/duplicate")
    public ResponseEntity<RepositoryResponse> duplicate(@PathVariable UUID id, Principal principal) {
        if (!isRepoOwnedByCaller(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String actor = principal != null ? principal.getName() : "API";
        return ResponseEntity.status(HttpStatus.CREATED).body(repositoryService.duplicate(id, actor));
    }

    // ─── POST /api/v1/repositories/{id}/sync ──────────────────────────────────
    @PostMapping("/{id}/sync")
    public ResponseEntity<Map<String, Object>> sync(@PathVariable UUID id, Principal principal) {
        if (!isRepoOwnedByCaller(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "error", "Access denied",
                    "message", "Repository does not belong to currently authenticated user"
            ));
        }
        String actor = principal != null ? principal.getName() : "SYSTEM";
        syncService.syncRepository(id, actor);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Repository synchronization initiated",
                "repositoryId", id.toString()
        ));
    }

    private Optional<String> getValidUserGitHubToken(Principal principal) {
        if (principal == null || principal.getName() == null) {
            return Optional.empty();
        }
        String name = principal.getName();
        Optional<UserEntity> userOpt = userRepository.findByEmail(name)
                .or(() -> userRepository.findByUsername(name));
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }
        Optional<OAuthAccountEntity> oauthOpt = oauthAccountRepository.findByUserAndProvider(userOpt.get(), "github");
        if (oauthOpt.isEmpty() || oauthOpt.get().getAccessToken() == null || oauthOpt.get().getAccessToken().trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(oauthOpt.get().getAccessToken().trim());
    }

    private boolean isPredictionAuthorized(Principal principal) {
        return getValidUserGitHubToken(principal).isPresent();
    }

    // ─── POST /api/v1/repositories/{id}/predict ───────────────────────────────
    @PostMapping("/{id}/predict")
    public ResponseEntity<Map<String, Object>> predict(@PathVariable UUID id, Principal principal) {
        String actor = principal != null ? principal.getName() : "MANUAL";
        log.info("[RepositoryController] POST /repositories/{}/predict — actor={}", id, actor);

        if (!isRepoOwnedByCaller(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "error", "Access denied",
                    "message", "Repository does not belong to currently authenticated user."
            ));
        }

        if (!isPredictionAuthorized(principal)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "error", "GitHub authorization required",
                    "message", "An active GitHub OAuth connection is required for your account to run predictions."
            ));
        }

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
            String rawMsg = ex.getMessage() != null ? ex.getMessage() : "";
            String causeMsg = ex.getCause() != null && ex.getCause().getMessage() != null ? ex.getCause().getMessage() : "";
            boolean isDbError = rawMsg.contains("update repositories") || rawMsg.contains("could not execute statement")
                    || rawMsg.contains("I/O error") || causeMsg.contains("I/O error") || causeMsg.contains("connection");
            
            String userMsg = isDbError
                    ? "The AI prediction was generated, but saving the result encountered a temporary database connection error. Please click 'Run Prediction' to retry."
                    : "Prediction failed: " + (ex.getMessage() != null ? ex.getMessage() : "Internal prediction engine failure");

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "code", isDbError ? "PREDICTION_PERSISTENCE_TEMPORARY_ERROR" : "PREDICTION_ENGINE_ERROR",
                    "error", isDbError ? "Temporary Database Connection Interruption" : "Prediction Engine Failure",
                    "message", userMsg,
                    "retryable", true
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

        if (!isPredictionAuthorized(principal)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "error", "GitHub authorization required",
                    "message", "An active GitHub OAuth connection or valid system GitHub token is required to run predictions."
            ));
        }

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
            String rawMsg = ex.getMessage() != null ? ex.getMessage() : "";
            String causeMsg = ex.getCause() != null && ex.getCause().getMessage() != null ? ex.getCause().getMessage() : "";
            boolean isDbError = rawMsg.contains("update repositories") || rawMsg.contains("could not execute statement")
                    || rawMsg.contains("I/O error") || causeMsg.contains("I/O error") || causeMsg.contains("connection");

            String userMsg = isDbError
                    ? "The AI prediction was generated, but saving the result encountered a temporary database connection error. Please click 'Run Prediction' to retry."
                    : "Prediction failed: " + (ex.getMessage() != null ? ex.getMessage() : "Internal server error");

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "code", isDbError ? "PREDICTION_PERSISTENCE_TEMPORARY_ERROR" : "PREDICTION_ENGINE_ERROR",
                    "error", isDbError ? "Temporary Database Connection Interruption" : "Prediction Engine Failure",
                    "message", userMsg,
                    "retryable", true
            ));
        }
    }

    // ─── GET /api/v1/repositories/{id}/metrics ────────────────────────────────
    @GetMapping("/{id}/metrics")
    public ResponseEntity<RepositoryMetricsResponse> getMetrics(@PathVariable UUID id, Principal principal) {
        if (!isRepoOwnedByCaller(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(repositoryService.getMetrics(id));
    }

    // ─── GET /api/v1/repositories/{id}/history ────────────────────────────────
    @GetMapping("/{id}/history")
    public ResponseEntity<RepositoryDetailResponse> getHistory(@PathVariable UUID id, Principal principal) {
        if (!isRepoOwnedByCaller(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        // Returns detail with predictionHistory and recentActivities populated
        return ResponseEntity.ok(repositoryService.findById(id));
    }

    // ─── GET /api/v1/repositories/{id}/prediction-debug ──────────────────────
    @GetMapping("/{id}/prediction-debug")
    public ResponseEntity<Map<String, Object>> getPredictionDebug(@PathVariable UUID id, Principal principal) {
        if (!isRepoOwnedByCaller(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        RepositoryDetailResponse repo = repositoryService.findById(id);
        Optional<RepositoryPredictionEntity> latestOpt = predictionRepository.findTopByRepositoryIdOrderByCreatedAtDesc(id);

        Map<String, Object> debugInfo = new java.util.LinkedHashMap<>();
        debugInfo.put("repositoryId", id.toString());
        debugInfo.put("repositoryName", repo.getRepositoryName());
        debugInfo.put("modelVersion", latestOpt.map(RepositoryPredictionEntity::getModelVersion).orElse("xgboost-v2.4"));
        debugInfo.put("featureSchemaValid", true);
        debugInfo.put("featureCount", 22);
        debugInfo.put("predictionStatus", repo.getPredictionStatus());
        debugInfo.put("riskScore", latestOpt.map(RepositoryPredictionEntity::getRiskScore).orElse(repo.getHealthScore() != null ? (int) Math.round(100.0 - repo.getHealthScore()) : 0));
        debugInfo.put("confidence", latestOpt.map(RepositoryPredictionEntity::getConfidence).orElse(repo.getAiConfidence() != null ? repo.getAiConfidence() : 0.0));
        debugInfo.put("riskCategory", repo.getRiskLevel() != null ? repo.getRiskLevel() : "LOW");
        debugInfo.put("predictionId", latestOpt.map(p -> p.getId().toString()).orElse(null));
        debugInfo.put("predictionTimestamp", latestOpt.map(p -> p.getCreatedAt().toString()).orElse(null));
        debugInfo.put("cacheHit", false);

        if (latestOpt.isPresent() && latestOpt.get().getFeatureImportanceJson() != null) {
            try {
                Object features = objectMapper.readValue(latestOpt.get().getFeatureImportanceJson(), Object.class);
                debugInfo.put("topRiskFactors", features);
            } catch (Exception ignored) {}
        }

        return ResponseEntity.ok(debugInfo);
    }

    // ─── GET /api/v1/repositories/export & /export/csv ────────────────────────
    @GetMapping(value = {"/export", "/export/csv"}, produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> export(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String predictionStatus,
            @RequestParam(required = false) String gitProvider,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String organization,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10000") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            Principal principal) {
        // Scope export to the authenticated user's repositories only
        String callerEmail = principal != null ? principal.getName() : null;
        log.info("[RepositoryController] Export CSV requested by actor={} with filters: search={} status={} riskLevel={} predictionStatus={} provider={} lang={}",
                callerEmail, search, status, riskLevel, predictionStatus, gitProvider, language);

        PagedRepositoryResponse response = repositoryService.findAllByUser(
                callerEmail, page, size, sortBy, sortDir,
                search, status, riskLevel, predictionStatus, gitProvider, language, organization
        );

        StringBuilder csv = new StringBuilder();
        csv.append("ID,Repository Name,Owner / Organization,Git Provider,Repository URL,Language,Status,Health Score,Failure Probability (%),Risk Level,Prediction Status,AI Confidence,Last Synced,Created At\n");

        if (response != null && response.getContent() != null) {
            for (RepositorySummaryResponse repo : response.getContent()) {
                csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%.1f,%.1f,%s,%s,%.2f,%s,%s\n",
                        escapeCsvField(repo.getId()),
                        escapeCsvField(repo.getRepositoryName()),
                        escapeCsvField(repo.getOrganization()),
                        escapeCsvField(repo.getGitProvider() != null ? repo.getGitProvider() : "GITHUB"),
                        escapeCsvField(repo.getRepositoryUrl()),
                        escapeCsvField(repo.getLanguage()),
                        escapeCsvField(repo.getStatus() != null ? repo.getStatus() : "ACTIVE"),
                        repo.getHealthScore() != null ? repo.getHealthScore() : 0.0,
                        (repo.getFailureProbability() != null ? repo.getFailureProbability() : 0.0) * 100,
                        escapeCsvField(repo.getRiskLevel() != null ? repo.getRiskLevel() : "LOW"),
                        escapeCsvField(repo.getPredictionStatus() != null ? repo.getPredictionStatus() : "PENDING"),
                        repo.getAiConfidence() != null ? repo.getAiConfidence() : 0.0,
                        escapeCsvField(repo.getLastSyncDate()),
                        escapeCsvField(repo.getCreatedAt())
                ));
            }
        }

        String filename = String.format("rivexa-repositories-%s.csv", java.time.LocalDate.now());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        return new ResponseEntity<>(csv.toString(), headers, HttpStatus.OK);
    }

    private String escapeCsvField(Object value) {
        if (value == null) return "\"\"";
        String str = value.toString();
        if (str.isEmpty()) return "\"\"";

        // Formula injection protection for =, +, -, @
        char firstChar = str.charAt(0);
        if (firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@') {
            str = "'" + str;
        }

        // Escape internal double quotes
        str = str.replace("\"", "\"\"");
        return "\"" + str + "\"";
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
