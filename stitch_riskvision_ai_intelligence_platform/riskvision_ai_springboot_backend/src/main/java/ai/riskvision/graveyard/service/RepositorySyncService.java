package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.client.*;
import ai.riskvision.graveyard.config.*;
import ai.riskvision.graveyard.entity.*;
import ai.riskvision.graveyard.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepositorySyncService {

    private final RepositoryEntityRepository repoRepository;
    private final RepositoryMetricsEntityRepository metricsRepository;
    private final RepositoryActivityEntityRepository activityRepository;
    private final GitHubClient gitHubClient;
    private final RestTemplate restTemplate;
    private final JwtTokenProvider jwtTokenProvider;
    private final N8nWebhookService n8nWebhookService;
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private RepoPredictionService predictionService;

    @Value("${ml.service.url:http://localhost:8000}")
    private String mlServiceUrl;

    private static class RepoInfo {
        String owner;
        String repo;
        
        RepoInfo(String owner, String repo) {
            this.owner = owner;
            this.repo = repo;
        }
    }

    private RepoInfo parseUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        try {
            String cleanUrl = url.trim().replaceAll("/+$", "").replaceAll("\\.git$", "");
            String[] parts = cleanUrl.split("/");
            if (parts.length >= 2) {
                String repo = parts[parts.length - 1];
                String owner = parts[parts.length - 2];
                return new RepoInfo(owner, repo);
            }
        } catch (Exception e) {
            log.warn("Failed to parse repository URL: {}", url, e);
        }
        return null;
    }

    /**
     * Performs a repository sync — updates lastSyncDate, refreshes metadata & metrics via GitHubClient.
     */
    @Transactional
    public void syncRepository(UUID repositoryId, String actor) {
        if (repositoryId == null) {
            throw new IllegalArgumentException("Repository ID must not be null");
        }
        RepositoryEntity entity = repoRepository.findById(repositoryId)
                .orElseThrow(() -> new NoSuchElementException("Repository not found: " + repositoryId));

        entity.setLastSyncDate(LocalDateTime.now());
        
        String provider = entity.getGitProvider() != null ? entity.getGitProvider().toUpperCase() : "GITHUB";
        RepoInfo info = parseUrl(entity.getRepositoryUrl());

        RepositoryMetricsEntity metrics = metricsRepository.findByRepositoryId(repositoryId)
                .orElseGet(() -> RepositoryMetricsEntity.builder().repositoryId(repositoryId).build());

        if (info != null && "GITHUB".equals(provider)) {
            try {
                log.info("Fetching GitHub API metadata for {}/{} via GitHubClient", info.owner, info.repo);
                Map<String, Object> body = gitHubClient.getRepositoryMetadata(info.owner, info.repo);
                
                if (body != null) {
                    if (body.get("description") != null) {
                        entity.setDescription((String) body.get("description"));
                    }
                    if (body.get("language") != null) {
                        entity.setLanguage((String) body.get("language"));
                    }
                    if (body.get("open_issues_count") != null) {
                        int issuesCount = ((Number) body.get("open_issues_count")).intValue();
                        entity.setOpenIssues(issuesCount);
                        metrics.setOpenIssues(issuesCount);
                    }
                    if (body.get("private") != null) {
                        entity.setVisibility(((Boolean) body.get("private")) ? "PRIVATE" : "PUBLIC");
                    }
                    if (body.get("license") instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> licenseMap = (Map<String, Object>) body.get("license");
                        if (licenseMap.get("name") != null) {
                            entity.setLicense((String) licenseMap.get("name"));
                        }
                    }
                    if (body.get("pushed_at") != null) {
                        try {
                            String pushedStr = (String) body.get("pushed_at");
                            java.time.Instant pushedInstant = java.time.Instant.parse(pushedStr);
                            long days = java.time.Duration.between(pushedInstant, java.time.Instant.now()).toDays();
                            metrics.setInactiveDays((int) Math.max(0, days));
                        } catch (Exception ignored) {}
                    }
                }

                // Fetch contributors
                try {
                    List<Map<String, Object>> contribList = gitHubClient.getContributors(info.owner, info.repo);
                    if (contribList != null) {
                        entity.setContributors(contribList.size());
                        metrics.setContributors(contribList.size());
                        metrics.setActiveContributors(Math.max(1, (int) Math.round(contribList.size() * 0.7)));
                        metrics.setBusFactor(Math.max(1, (int) Math.round(contribList.size() * 0.3)));
                    }
                } catch (Exception ex) {
                    log.warn("Failed to fetch contributors for GitHub repo {}/{}: {}", info.owner, info.repo, ex.getMessage());
                }

                // Fetch commits (up to 100)
                try {
                    List<Map<String, Object>> commits = gitHubClient.getCommits(info.owner, info.repo, null, 1, 100);
                    if (commits != null) {
                        metrics.setCommitCount(commits.size());
                        metrics.setCommitFrequency(Math.round((commits.size() / 30.0) * 100.0) / 100.0);
                    }
                } catch (Exception ex) {
                    log.warn("Failed to fetch commits for GitHub repo {}/{}: {}", info.owner, info.repo, ex.getMessage());
                }

                // Fetch PRs (open & closed)
                try {
                    List<Map<String, Object>> openPrs = gitHubClient.getPullRequests(info.owner, info.repo, "open");
                    List<Map<String, Object>> closedPrs = gitHubClient.getPullRequests(info.owner, info.repo, "closed");
                    int totalPrs = (openPrs != null ? openPrs.size() : 0) + (closedPrs != null ? closedPrs.size() : 0);
                    int mergedPrs = 0;
                    if (closedPrs != null) {
                        for (Map<String, Object> pr : closedPrs) {
                            if (Boolean.TRUE.equals(pr.get("merged")) || pr.get("merged_at") != null) {
                                mergedPrs++;
                            }
                        }
                    }
                    metrics.setPullRequests(totalPrs);
                    metrics.setMergedPullRequests(mergedPrs);
                    metrics.setFailedPullRequests(Math.max(0, (closedPrs != null ? closedPrs.size() : 0) - mergedPrs));
                } catch (Exception ex) {
                    log.warn("Failed to fetch pull requests for GitHub repo {}/{}: {}", info.owner, info.repo, ex.getMessage());
                }

                // Fetch README & codebase completeness
                try {
                    Map<String, Object> readme = gitHubClient.getReadme(info.owner, info.repo);
                    if (readme != null && readme.get("size") != null) {
                        long size = ((Number) readme.get("size")).longValue();
                        metrics.setDocumentationScore(Math.min(100.0, Math.round(size / 50.0)));
                    } else {
                        metrics.setDocumentationScore(30.0);
                    }
                } catch (Exception ignored) {
                    metrics.setDocumentationScore(20.0);
                }

                // Fetch workflows & estimate CI/CD health and test coverage
                try {
                    Map<String, Object> workflowsMap = gitHubClient.getWorkflows(info.owner, info.repo);
                    Object totalWorkflowsObj = workflowsMap != null ? workflowsMap.get("total_count") : null;
                    int workflowCount = totalWorkflowsObj instanceof Number n ? n.intValue() : 0;

                    double baseCoverage = workflowCount > 0 ? 82.0 : 35.0;
                    if (metrics.getDocumentationScore() != null && metrics.getDocumentationScore() < 50.0) {
                        baseCoverage = Math.max(15.0, baseCoverage - 15.0);
                    }
                    metrics.setCodeCoverage(Math.min(95.0, Math.max(10.0, baseCoverage)));

                    int totalPrs = metrics.getPullRequests() != null ? metrics.getPullRequests() : 0;
                    int mergedPrs = metrics.getMergedPullRequests() != null ? metrics.getMergedPullRequests() : 0;
                    if (totalPrs > 0) {
                        metrics.setBuildSuccessRate(Math.min(98.0, Math.max(50.0, (mergedPrs * 100.0) / totalPrs)));
                    } else {
                        metrics.setBuildSuccessRate(workflowCount > 0 ? 92.0 : 75.0);
                    }
                } catch (Exception ex) {
                    log.warn("Failed to fetch workflows for GitHub repo {}/{}: {}", info.owner, info.repo, ex.getMessage());
                    metrics.setCodeCoverage(45.0);
                    metrics.setBuildSuccessRate(80.0);
                }

                int openIssuesCount = metrics.getOpenIssues() != null ? metrics.getOpenIssues() : 0;
                int failedPrsCount = metrics.getFailedPullRequests() != null ? metrics.getFailedPullRequests() : 0;
                int inactiveDaysCount = metrics.getInactiveDays() != null ? metrics.getInactiveDays() : 0;
                int commitsCount = metrics.getCommitCount() != null ? metrics.getCommitCount() : 0;

                metrics.setTechnicalDebt(Math.max(0.0, (openIssuesCount * 2.5) + (failedPrsCount * 4.0) + (inactiveDaysCount > 30 ? 15.0 : 0.0)));
                metrics.setVelocity(Math.max(1.0, (commitsCount * 0.4) + ((metrics.getMergedPullRequests() != null ? metrics.getMergedPullRequests() : 0) * 1.2)));

            } catch (Exception e) {
                log.warn("Failed to sync metrics from Git API provider ({}). Error: {}", provider, e.getMessage());
                if (entity.getContributors() == null || entity.getContributors() == 0) {
                    entity.setContributors(1);
                }
            }
        }

        // Calculate initial telemetry-derived baseline health score & failure probability if no prediction run yet
        if ("PENDING".equalsIgnoreCase(entity.getPredictionStatus()) || entity.getHealthScore() == null || entity.getHealthScore() == 0.0) {
            double buildRate = metrics.getBuildSuccessRate() != null ? metrics.getBuildSuccessRate() : 85.0;
            double coverage = metrics.getCodeCoverage() != null ? metrics.getCodeCoverage() : 50.0;
            double docScore = metrics.getDocumentationScore() != null ? metrics.getDocumentationScore() : 50.0;
            int inactive = metrics.getInactiveDays() != null ? metrics.getInactiveDays() : 0;
            int issues = metrics.getOpenIssues() != null ? metrics.getOpenIssues() : 0;

            double calcHealth = Math.min(100.0, Math.max(15.0, (buildRate * 0.4) + (coverage * 0.3) + (docScore * 0.2) - (Math.min(30, inactive) * 0.5) - (Math.min(20, issues) * 1.0)));
            double calcFailProb = Math.max(0.01, Math.min(0.99, (100.0 - calcHealth) / 100.0));
            String calcRisk = calcFailProb < 0.25 ? "LOW" : calcFailProb < 0.55 ? "MEDIUM" : calcFailProb < 0.80 ? "HIGH" : "CRITICAL";

            entity.setHealthScore(Math.round(calcHealth * 10.0) / 10.0);
            entity.setFailureProbability(Math.round(calcFailProb * 1000.0) / 1000.0);
            entity.setRiskLevel(calcRisk);
        }

        repoRepository.save(entity);
        metricsRepository.save(metrics);

        syncRepositoryToFastApi(entity);

        logActivity(repositoryId, "REPOSITORY_SYNCED",
                "Repository '" + entity.getRepositoryName() + "' synchronized with " + entity.getGitProvider(),
                actor, "SYNC", "INFO");

        log.info("Repository synced: {} by {}", repositoryId, actor);

        try {
            n8nWebhookService.triggerRepositorySyncWebhook(
                    entity.getId().toString(),
                    entity.getRepositoryName(),
                    entity.getGitProvider(),
                    true,
                    "Repository synced successfully by " + (actor != null ? actor : "SYSTEM")
            );
        } catch (Exception e) {
            log.warn("Non-critical error sending n8n webhook for single repository sync: {}", e.getMessage());
        }
    }

    /**
     * Replicates repository metadata to Python FastAPI projects persistence endpoint.
     */
    public void syncRepositoryToFastApi(RepositoryEntity entity) {
        if (entity == null || mlServiceUrl == null) return;
        try {
            String url = mlServiceUrl + "/api/v1/projects";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            if (jwtTokenProvider != null) {
                String token = jwtTokenProvider.generateAccessToken(
                        "admin@riskvision.ai", "admin", "admin-uuid-placeholder", "email", "admin"
                );
                headers.set("Authorization", "Bearer " + token);
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("external_id", entity.getId() != null ? entity.getId().toString() : UUID.randomUUID().toString());
            payload.put("name", entity.getRepositoryName());
            payload.put("description", entity.getDescription() != null ? entity.getDescription() : "GitHub Repository");
            payload.put("team_size", entity.getContributors() != null ? (double) entity.getContributors() : 1.0);
            payload.put("budget", 100000.0);
            payload.put("actual_cost", 85000.0);
            payload.put("timeline_months", 12.0);
            payload.put("actual_duration", 10.0);
            payload.put("status", "active");

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            restTemplate.postForLocation(url, request);
            log.info("Successfully synchronized repository {} to FastAPI projects database", entity.getRepositoryName());
        } catch (Exception e) {
            log.warn("FastAPI project sync skipped or unavailable: {}", e.getMessage());
        }
    }

    @Transactional
    public void syncAllRepositories() {
        List<RepositoryEntity> repositories = repoRepository.findAll();
        log.info("Starting background synchronization for {} repositories...", repositories.size());
        for (RepositoryEntity repo : repositories) {
            try {
                syncRepository(repo.getId(), "SYSTEM_SCHEDULED");
            } catch (Exception e) {
                log.error("Failed scheduled sync for repository {}: {}", repo.getId(), e.getMessage());
            }
        }
    }

    /**
     * Synchronizes and persists all GitHub repositories for the given authenticated user.
     * Fetches repos across pages from GitHub API, creates/updates RepositoryEntity linked to user,
     * updates metrics, marks inaccessible repos inactive, and triggers XGBoost risk predictions.
     */
    private final Map<UUID, Boolean> activeSyncLocks = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Object>> latestSyncDebugInfo = new java.util.concurrent.ConcurrentHashMap<>();

    public Map<String, Object> getLastSyncDebugInfo(UUID userId) {
        if (userId == null) return Map.of("syncStatus", "NO_SYNC_DATA");
        return latestSyncDebugInfo.getOrDefault(userId, Map.of(
                "userId", userId.toString(),
                "syncStatus", "NOT_TRIGGERED_YET",
                "message", "No repository sync executed for user in current application session"
        ));
    }

    /**
     * Synchronizes and persists all GitHub repositories for the given authenticated user.
     * Validates access token, fetches across pages from GitHub API, deduplicates by immutable GitHub repo ID,
     * performs idempotent upsert, updates metrics, marks inaccessible repos inactive, and triggers XGBoost predictions.
     */
    @Transactional
    public List<RepositoryEntity> syncUserGitHubRepositories(UserEntity user, String userToken) {
        if (user == null || userToken == null || userToken.trim().isEmpty()) {
            log.warn("[GITHUB SYNC] Cannot sync GitHub repositories: missing user or token.");
            return java.util.Collections.emptyList();
        }

        UUID userId = user.getId();

        // 1. Prevent concurrent duplicate synchronization requests
        if (activeSyncLocks.putIfAbsent(userId, Boolean.TRUE) != null) {
            log.warn("[GITHUB SYNC] Synchronization already in progress for user id={}, email={}. Returning existing records.", userId, user.getEmail());
            return repoRepository.findByUserIdAndGitProvider(userId, "GITHUB");
        }

        long startMs = System.currentTimeMillis();
        List<RepositoryEntity> syncedRepos = new java.util.ArrayList<>();
        java.util.Set<UUID> fetchedRepoIds = new java.util.HashSet<>();

        try {
            // 2. Validate token & user profile
            try {
                gitHubClient.getAuthenticatedUserProfile(userToken.trim());
            } catch (Exception authEx) {
                log.error("[GITHUB SYNC] GitHub access token validation failed for user email={}: {}", user.getEmail(), authEx.getMessage());
                latestSyncDebugInfo.put(userId, Map.of(
                        "userId", userId.toString(),
                        "syncStatus", "FAILED",
                        "errorCode", "GITHUB_AUTH_FAILED",
                        "message", "GitHub authentication is no longer valid: " + authEx.getMessage()
                ));
                throw new ai.riskvision.graveyard.exception.GitHubAuthenticationException("GitHub token is no longer valid.", "/user", authEx.getMessage());
            }

            log.info("[GITHUB SYNC] Starting GitHub repository synchronization for user id={}, email={}", userId, user.getEmail());

            // 3. Multi-page fetching and in-memory deduplication by immutable GitHub repository ID
            Map<String, Map<String, Object>> uniqueGithubRepos = new java.util.LinkedHashMap<>();
            int page = 1;
            int perPage = 100;
            int pagesFetched = 0;
            int totalRawFetched = 0;
            boolean hasMore = true;

            while (hasMore) {
                List<Map<String, Object>> rawPage = gitHubClient.getUserRepositories(userToken.trim(), page, perPage, "all", "owner,collaborator,organization_member", "updated");
                if (rawPage == null || rawPage.isEmpty()) {
                    hasMore = false;
                    break;
                }

                pagesFetched++;
                totalRawFetched += rawPage.size();

                for (Map<String, Object> repo : rawPage) {
                    Object idObj = repo.get("id");
                    String ghId = idObj != null ? idObj.toString() : (String) repo.get("node_id");
                    if (ghId == null || ghId.trim().isEmpty()) {
                        ghId = (String) repo.get("full_name");
                    }
                    if (ghId != null && !ghId.trim().isEmpty()) {
                        uniqueGithubRepos.putIfAbsent(ghId.trim(), repo);
                    }
                }

                log.info("[GITHUB SYNC] Page {} fetched {} repositories for user email={}. Unique repositories so far: {}",
                        page, rawPage.size(), user.getEmail(), uniqueGithubRepos.size());

                if (rawPage.size() < perPage) {
                    hasMore = false;
                } else {
                    page++;
                }
            }

            int createdCount = 0;
            int updatedCount = 0;

            // 4. Idempotent Upsert into Database
            for (Map.Entry<String, Map<String, Object>> entry : uniqueGithubRepos.entrySet()) {
                String ghRepositoryId = entry.getKey();
                Map<String, Object> repo = entry.getValue();

                try {
                    String repoName = (String) repo.get("name");
                    String htmlUrl = (String) repo.get("html_url");
                    if (repoName == null || repoName.trim().isEmpty()) continue;

                    String ownerLogin = user.getUsername() != null ? user.getUsername() : user.getEmail().split("@")[0];
                    Object ownerObj = repo.get("owner");
                    if (ownerObj instanceof Map<?, ?> ownerMap && ownerMap.get("login") != null) {
                        ownerLogin = ownerMap.get("login").toString();
                    }

                    String normalizedUrl = htmlUrl != null ? htmlUrl.trim().replaceAll("/+$", "").replaceAll("\\.git$", "") : ("https://github.com/" + ownerLogin + "/" + repoName);

                    String description = repo.get("description") != null ? (String) repo.get("description") : null;
                    String language = repo.get("language") != null ? (String) repo.get("language") : null;
                    Boolean isPrivate = repo.get("private") instanceof Boolean p ? p : false;
                    String visibility = repo.get("visibility") != null ? (String) repo.get("visibility") : (isPrivate ? "PRIVATE" : "PUBLIC");
                    String defaultBranch = repo.get("default_branch") != null ? (String) repo.get("default_branch") : "main";

                    int openIssues = repo.get("open_issues_count") instanceof Number n ? n.intValue() : 0;
                    int stars = repo.get("stargazers_count") instanceof Number n ? n.intValue() : 0;
                    int forks = repo.get("forks_count") instanceof Number n ? n.intValue() : 0;

                    String licenseName = null;
                    if (repo.get("license") instanceof Map<?, ?> licMap && licMap.get("name") != null) {
                        licenseName = licMap.get("name").toString();
                    }

                    // Look up existing repository by (userId, githubRepositoryId) -> (userId, normalizedUrl) -> (userId, repoName)
                    Optional<RepositoryEntity> existingOpt = repoRepository.findByUser_IdAndGithubRepositoryId(userId, ghRepositoryId)
                            .or(() -> repoRepository.findByUser_IdAndRepositoryUrl(userId, normalizedUrl))
                            .or(() -> repoRepository.findByUser_IdAndRepositoryName(userId, repoName));

                    boolean isNew = existingOpt.isEmpty();
                    if (isNew) createdCount++; else updatedCount++;

                    RepositoryEntity entity = existingOpt.orElseGet(() -> RepositoryEntity.builder()
                            .user(user)
                            .githubRepositoryId(ghRepositoryId)
                            .repositoryName(repoName)
                            .repositoryUrl(normalizedUrl)
                            .gitProvider("GITHUB")
                            .status("ACTIVE")
                            .predictionStatus("PENDING")
                            .lifecycleStage("ACTIVE")
                            .riskLevel("LOW")
                            .healthScore(0.0)
                            .failureProbability(0.0)
                            .aiConfidence(0.0)
                            .build());

                    entity.setUser(user);
                    entity.setGithubRepositoryId(ghRepositoryId);
                    entity.setRepositoryName(repoName);
                    entity.setDescription(description);
                    entity.setOrganization(ownerLogin);
                    entity.setOwner(ownerLogin);
                    entity.setRepositoryUrl(normalizedUrl);
                    entity.setGitProvider("GITHUB");
                    entity.setBranch(defaultBranch);
                    entity.setLanguage(language);
                    entity.setVisibility(visibility);
                    entity.setLicense(licenseName);
                    entity.setOpenIssues(openIssues);
                    entity.setStatus("ACTIVE");
                    entity.setLastSyncDate(LocalDateTime.now());

                    entity = repoRepository.save(entity);

                    UUID entityId = entity.getId();
                    fetchedRepoIds.add(entityId);

                    RepositoryMetricsEntity metrics = metricsRepository.findByRepositoryId(entityId)
                            .orElseGet(() -> RepositoryMetricsEntity.builder().repositoryId(entityId).build());

                    metrics.setOpenIssues(openIssues);
                    int commitEstimate = Math.max(5, stars * 3 + forks * 2 + openIssues);
                    metrics.setCommitCount(commitEstimate);
                    metrics.setCommitFrequency(Math.round((commitEstimate / 30.0) * 100.0) / 100.0);
                    int contribCount = Math.max(1, forks + (stars > 5 ? 2 : 1));
                    metrics.setContributors(contribCount);
                    metrics.setActiveContributors(Math.max(1, (int) Math.round(contribCount * 0.6)));
                    metrics.setBusFactor(Math.max(1, (int) Math.round(contribCount * 0.3)));
                    metrics.setInactiveDays(0);
                    metrics.setDocumentationScore(description != null && !description.isBlank() ? 85.0 : 40.0);
                    metrics.setCodeCoverage(75.0);
                    metrics.setBuildSuccessRate(90.0);
                    metrics.setTechnicalDebt((double) openIssues * 1.5);
                    metricsRepository.save(metrics);

                    syncedRepos.add(entity);
                } catch (Exception repoEx) {
                    log.warn("[GITHUB SYNC] Could not sync repository item for user {}: {}", user.getEmail(), repoEx.getMessage());
                }
            }

            int inactiveCount = 0;
            // 5. Mark user repositories no longer accessible via GitHub API as INACTIVE
            try {
                org.springframework.data.domain.Page<RepositoryEntity> existingUserRepos =
                        repoRepository.findAllByUserWithFilters(userId, null, null, null, null, "GITHUB", null, null,
                                org.springframework.data.domain.PageRequest.of(0, 1000));
                for (RepositoryEntity existing : existingUserRepos.getContent()) {
                    if ("GITHUB".equalsIgnoreCase(existing.getGitProvider()) && !fetchedRepoIds.contains(existing.getId())) {
                        log.info("[GITHUB SYNC] Marking repository id={}, name={} as INACTIVE (no longer accessible via GitHub API)",
                                existing.getId(), existing.getRepositoryName());
                        existing.setStatus("INACTIVE");
                        repoRepository.save(existing);
                        inactiveCount++;
                    }
                }
            } catch (Exception markEx) {
                log.warn("[GITHUB SYNC] Could not update inactive status for unaccessible repositories: {}", markEx.getMessage());
            }

            long durationMs = System.currentTimeMillis() - startMs;

            // 6. Save Debug Telemetry
            Map<String, Object> debugInfo = new java.util.LinkedHashMap<>();
            debugInfo.put("userId", userId.toString());
            debugInfo.put("userEmail", user.getEmail());
            debugInfo.put("syncStatus", "SUCCESS");
            debugInfo.put("pagesFetched", pagesFetched);
            debugInfo.put("rawRepositoriesFetched", totalRawFetched);
            debugInfo.put("uniqueRepositories", uniqueGithubRepos.size());
            debugInfo.put("repositoriesCreated", createdCount);
            debugInfo.put("repositoriesUpdated", updatedCount);
            debugInfo.put("repositoriesMarkedInactive", inactiveCount);
            debugInfo.put("syncDurationMs", durationMs);
            debugInfo.put("timestamp", LocalDateTime.now().toString());
            latestSyncDebugInfo.put(userId, debugInfo);

            log.info("[GITHUB-SYNC] appUser={} githubId={} githubLogin={} githubApiRepositoryCount={} databaseRepositoryCount={} apiReturnedRepositoryCount={} syncTimestamp={}",
                    user.getId(), user.getGithubId(), user.getUsername(), uniqueGithubRepos.size(), syncedRepos.size(), syncedRepos.size(), LocalDateTime.now());

            log.info("[GITHUB SYNC] Synchronized {} unique repositories for user email={}. Raw fetched={}, Pages={}, Duration={}ms",
                    syncedRepos.size(), user.getEmail(), totalRawFetched, pagesFetched, durationMs);

            // 7. Run Real XGBoost Risk Predictions asynchronously in background
            if (predictionService != null && !syncedRepos.isEmpty()) {
                final List<RepositoryEntity> reposToPredict = new java.util.ArrayList<>(syncedRepos);
                final String actorEmail = user.getEmail();
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    for (RepositoryEntity repo : reposToPredict) {
                        try {
                            log.info("[GITHUB SYNC ASYNC] Executing XGBoost prediction for repository id={}, name={}", repo.getId(), repo.getRepositoryName());
                            predictionService.runPrediction(repo.getId(), actorEmail);
                        } catch (Exception predEx) {
                            log.warn("[GITHUB SYNC ASYNC] Single repository prediction failed for id={}, name={}: {}. Preserving repository record.",
                                    repo.getId(), repo.getRepositoryName(), predEx.getMessage());
                        }
                    }
                });
            }

            try {
                n8nWebhookService.triggerRepositorySyncWebhook(
                        "batch-" + userId,
                        user.getEmail() + " GitHub Repositories",
                        "GITHUB",
                        true,
                        "Synchronized " + syncedRepos.size() + " repositories for user " + user.getEmail()
                );
            } catch (Exception e) {
                log.warn("Non-critical error sending n8n webhook for batch user repository sync: {}", e.getMessage());
            }

            return syncedRepos;

        } finally {
            activeSyncLocks.remove(userId);
        }
    }

    /**
     * Logs an activity event for a repository — used by all services to maintain audit trail.
     */
    @Transactional
    public void logActivity(UUID repositoryId, String action, String description,
                            String actor, String resourceType, String severity) {
        if (repositoryId == null) {
            throw new IllegalArgumentException("Repository ID must not be null");
        }
        RepositoryActivityEntity activity = RepositoryActivityEntity.builder()
                .repositoryId(repositoryId)
                .action(action)
                .description(description)
                .actor(actor != null ? actor : "SYSTEM")
                .resourceType(resourceType)
                .severity(severity != null ? severity : "INFO")
                .build();
        activityRepository.save(activity);
    }
}
