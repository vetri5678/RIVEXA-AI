package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.client.GitHubClient;
import ai.riskvision.graveyard.entity.OAuthAccountEntity;
import ai.riskvision.graveyard.entity.UserEntity;
import ai.riskvision.graveyard.exception.*;
import ai.riskvision.graveyard.repository.OAuthAccountRepository;
import ai.riskvision.graveyard.repository.RepositoryEntityRepository;
import ai.riskvision.graveyard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class GitHubController {

    private final GitHubClient gitHubClient;
    private final UserRepository userRepository;
    private final OAuthAccountRepository oauthAccountRepository;
    private final RepositoryEntityRepository repositoryEntityRepository;
    private final ai.riskvision.graveyard.repository.RepositoryPredictionEntityRepository predictionRepository;
    private final ai.riskvision.graveyard.repository.RepositoryMetricsEntityRepository repoMetricsRepository;
    private final ai.riskvision.graveyard.repository.RepositoryActivityEntityRepository activityRepository;
    private final ai.riskvision.graveyard.service.RepositorySyncService repositorySyncService;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    // ─── HEALTH & RATE LIMIT ───────────────────────────────────────────────────

    @GetMapping({"/api/github/health", "/api/v1/github/health"})
    public ResponseEntity<Map<String, Object>> getHealthStatus() {
        log.debug("HTTP GET /api/github/health requested");
        return ResponseEntity.ok(gitHubClient.getHealthStatus());
    }

    @GetMapping({"/api/github/rate-limit", "/api/v1/github/rate-limit"})
    public ResponseEntity<Map<String, Object>> getRateLimit() {
        log.debug("HTTP GET /api/github/rate-limit requested");
        return ResponseEntity.ok(gitHubClient.getRateLimit());
    }

    @GetMapping({"/api/v1/debug/github-sync", "/api/debug/github-sync"})
    public ResponseEntity<Map<String, Object>> getSyncDebugInfo(Principal principal) {
        String principalName = principal != null ? principal.getName() : null;
        if (principalName == null) {
            return ResponseEntity.ok(Map.of(
                    "syncStatus", "UNAUTHENTICATED",
                    "message", "Must be logged in to view GitHub sync debug info"
            ));
        }

        Optional<UserEntity> userOpt = userRepository.findByEmail(principalName)
                .or(() -> userRepository.findByUsername(principalName));
        if (userOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "syncStatus", "USER_NOT_FOUND",
                    "message", "Authenticated user record not found"
            ));
        }

        UserEntity user = userOpt.get();
        return ResponseEntity.ok(repositorySyncService.getLastSyncDebugInfo(user.getId()));
    }

    // ─── REPOSITORY METADATA & DATA ────────────────────────────────────────────

    @GetMapping({"/api/github/repositories/{owner}/{repo}", "/api/v1/github/repos/{owner}/{repo}"})
    public ResponseEntity<Map<String, Object>> getRepositoryMetadata(
            @PathVariable String owner,
            @PathVariable String repo) {
        return ResponseEntity.ok(gitHubClient.getRepositoryMetadata(owner, repo));
    }

    @GetMapping({"/api/github/repositories/{owner}/{repo}/stats", "/api/v1/github/repos/{owner}/{repo}/stats"})
    public ResponseEntity<Map<String, Object>> getRepositoryStats(
            @PathVariable String owner,
            @PathVariable String repo) {
        return ResponseEntity.ok(gitHubClient.getRepositoryStats(owner, repo));
    }

    @GetMapping({"/api/github/languages", "/api/v1/github/repos/{owner}/{repo}/languages"})
    public ResponseEntity<Map<String, Object>> getLanguages(
            @RequestParam(required = false, defaultValue = "vetri5678") String owner,
            @RequestParam(required = false, defaultValue = "riskprediction-ai-") String repo) {
        return ResponseEntity.ok(gitHubClient.getLanguages(owner, repo));
    }

    @GetMapping("/api/v1/github/repos/{owner}/{repo}/branches")
    public ResponseEntity<List<Map<String, Object>>> getBranches(
            @PathVariable String owner,
            @PathVariable String repo) {
        return ResponseEntity.ok(gitHubClient.getBranches(owner, repo));
    }

    @GetMapping({"/api/github/commits", "/api/v1/github/repos/{owner}/{repo}/commits"})
    public ResponseEntity<List<Map<String, Object>>> getCommits(
            @RequestParam(required = false, defaultValue = "vetri5678") String owner,
            @RequestParam(required = false, defaultValue = "riskprediction-ai-") String repo,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "30") Integer perPage) {
        return ResponseEntity.ok(gitHubClient.getCommits(owner, repo, branch, page, perPage));
    }

    @GetMapping("/api/v1/github/repos/{owner}/{repo}/commits/latest")
    public ResponseEntity<Map<String, Object>> getLatestCommit(
            @PathVariable String owner,
            @PathVariable String repo) {
        return ResponseEntity.ok(gitHubClient.getLatestCommit(owner, repo));
    }

    @GetMapping({"/api/github/contributors", "/api/v1/github/repos/{owner}/{repo}/contributors"})
    public ResponseEntity<List<Map<String, Object>>> getContributors(
            @RequestParam(required = false, defaultValue = "vetri5678") String owner,
            @RequestParam(required = false, defaultValue = "riskprediction-ai-") String repo) {
        return ResponseEntity.ok(gitHubClient.getContributors(owner, repo));
    }

    @GetMapping({"/api/github/pulls", "/api/v1/github/repos/{owner}/{repo}/pulls"})
    public ResponseEntity<List<Map<String, Object>>> getPullRequests(
            @RequestParam(required = false, defaultValue = "vetri5678") String owner,
            @RequestParam(required = false, defaultValue = "riskprediction-ai-") String repo,
            @RequestParam(required = false, defaultValue = "open") String state) {
        return ResponseEntity.ok(gitHubClient.getPullRequests(owner, repo, state));
    }

    @GetMapping({"/api/github/issues", "/api/v1/github/repos/{owner}/{repo}/issues"})
    public ResponseEntity<List<Map<String, Object>>> getIssues(
            @RequestParam(required = false, defaultValue = "vetri5678") String owner,
            @RequestParam(required = false, defaultValue = "riskprediction-ai-") String repo,
            @RequestParam(required = false, defaultValue = "open") String state) {
        return ResponseEntity.ok(gitHubClient.getIssues(owner, repo, state));
    }

    @GetMapping("/api/v1/github/repos/{owner}/{repo}/releases")
    public ResponseEntity<List<Map<String, Object>>> getReleases(
            @PathVariable String owner,
            @PathVariable String repo) {
        return ResponseEntity.ok(gitHubClient.getReleases(owner, repo));
    }

    @GetMapping("/api/v1/github/repos/{owner}/{repo}/tags")
    public ResponseEntity<List<Map<String, Object>>> getTags(
            @PathVariable String owner,
            @PathVariable String repo) {
        return ResponseEntity.ok(gitHubClient.getTags(owner, repo));
    }

    @GetMapping({"/api/github/actions", "/api/v1/github/repos/{owner}/{repo}/workflows"})
    public ResponseEntity<Map<String, Object>> getWorkflows(
            @RequestParam(required = false, defaultValue = "vetri5678") String owner,
            @RequestParam(required = false, defaultValue = "riskprediction-ai-") String repo) {
        return ResponseEntity.ok(gitHubClient.getWorkflows(owner, repo));
    }

    @GetMapping({"/api/github/security", "/api/v1/github/repos/{owner}/{repo}/alerts/dependabot"})
    public ResponseEntity<List<Map<String, Object>>> getDependabotAlerts(
            @RequestParam(required = false, defaultValue = "vetri5678") String owner,
            @RequestParam(required = false, defaultValue = "riskprediction-ai-") String repo) {
        return ResponseEntity.ok(gitHubClient.getDependabotAlerts(owner, repo));
    }

    @GetMapping("/api/v1/github/repos/{owner}/{repo}/alerts/code-scanning")
    public ResponseEntity<List<Map<String, Object>>> getCodeScanningAlerts(
            @PathVariable String owner,
            @PathVariable String repo) {
        return ResponseEntity.ok(gitHubClient.getCodeScanningAlerts(owner, repo));
    }

    @GetMapping("/api/v1/github/repos/{owner}/{repo}/alerts/secret-scanning")
    public ResponseEntity<List<Map<String, Object>>> getSecretScanningAlerts(
            @PathVariable String owner,
            @PathVariable String repo) {
        return ResponseEntity.ok(gitHubClient.getSecretScanningAlerts(owner, repo));
    }

    @GetMapping("/api/v1/github/repos/{owner}/{repo}/topics")
    public ResponseEntity<Map<String, Object>> getTopics(
            @PathVariable String owner,
            @PathVariable String repo) {
        return ResponseEntity.ok(gitHubClient.getTopics(owner, repo));
    }

    @GetMapping("/api/v1/github/repos/{owner}/{repo}/license")
    public ResponseEntity<Map<String, Object>> getLicense(
            @PathVariable String owner,
            @PathVariable String repo) {
        return ResponseEntity.ok(gitHubClient.getLicense(owner, repo));
    }

    @GetMapping({"/api/github/readme", "/api/v1/github/repos/{owner}/{repo}/readme"})
    public ResponseEntity<Map<String, Object>> getReadme(
            @RequestParam(required = false, defaultValue = "vetri5678") String owner,
            @RequestParam(required = false, defaultValue = "riskprediction-ai-") String repo) {
        return ResponseEntity.ok(gitHubClient.getReadme(owner, repo));
    }

    @GetMapping("/api/v1/github/repos/{owner}/{repo}/tree")
    public ResponseEntity<Map<String, Object>> getTree(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam(required = false, defaultValue = "main") String treeSha,
            @RequestParam(required = false, defaultValue = "false") Boolean recursive) {
        return ResponseEntity.ok(gitHubClient.getTree(owner, repo, treeSha, recursive));
    }

    @GetMapping("/api/v1/github/repos/{owner}/{repo}/contents")
    public ResponseEntity<Map<String, Object>> getFileContent(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam(required = false, defaultValue = "") String path) {
        return ResponseEntity.ok(gitHubClient.getFileContent(owner, repo, path));
    }

    // ─── SEARCH & USERS / ORGS ───────────────────────────────────────────────

    @GetMapping({"/api/github/search", "/api/github/repositories", "/api/v1/github/search/repositories"})
    public ResponseEntity<Map<String, Object>> searchRepositories(@RequestParam(required = false, defaultValue = "stars:>1000") String q) {
        return ResponseEntity.ok(gitHubClient.searchRepositories(q));
    }

    @GetMapping("/api/v1/github/search/code")
    public ResponseEntity<Map<String, Object>> searchCode(@RequestParam String q) {
        return ResponseEntity.ok(gitHubClient.searchCode(q));
    }

    @GetMapping("/api/v1/github/search/users")
    public ResponseEntity<Map<String, Object>> searchUsers(@RequestParam String q) {
        return ResponseEntity.ok(gitHubClient.searchUsers(q));
    }

    @GetMapping("/api/v1/github/search/orgs")
    public ResponseEntity<Map<String, Object>> searchOrganizations(@RequestParam String q) {
        return ResponseEntity.ok(gitHubClient.searchOrganizations(q));
    }

    @GetMapping("/api/v1/github/search/commits")
    public ResponseEntity<Map<String, Object>> searchCommits(@RequestParam String q) {
        return ResponseEntity.ok(gitHubClient.searchCommits(q));
    }

    @GetMapping({"/api/github/profile", "/api/v1/github/user/profile"})
    public ResponseEntity<Map<String, Object>> getUserProfile() {
        return ResponseEntity.ok(gitHubClient.getUserProfile());
    }

    // ─── CONNECTION STATUS & DISCONNECT ────────────────────────────────────────

    @GetMapping({"/api/v1/github/connection/status", "/api/github/connection/status"})
    public ResponseEntity<Map<String, Object>> getConnectionStatus(Principal principal) {
        String principalName = principal != null ? principal.getName() : null;
        if (principalName == null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("connected", false);
            result.put("status", "DISCONNECTED");
            result.put("githubUser", null);
            result.put("repositoryCount", 0);
            return ResponseEntity.ok(result);
        }

        Optional<UserEntity> userOpt = userRepository.findByEmail(principalName)
                .or(() -> userRepository.findByUsername(principalName));
        if (userOpt.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("connected", false);
            result.put("status", "DISCONNECTED");
            result.put("githubUser", null);
            result.put("repositoryCount", 0);
            return ResponseEntity.ok(result);
        }

        UserEntity user = userOpt.get();
        Optional<OAuthAccountEntity> oauthOpt = oauthAccountRepository.findByUserAndProvider(user, "github");
        boolean hasOAuthToken = oauthOpt.isPresent() && oauthOpt.get().getAccessToken() != null && !oauthOpt.get().getAccessToken().trim().isEmpty();

        // STRICT AUTHORITATIVE RULE: GitHub connection requires a valid OAuthAccountEntity with an active token belonging to current user
        if (!hasOAuthToken) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("connected", false);
            result.put("status", "DISCONNECTED");
            result.put("githubUser", null);
            result.put("repositoryCount", 0);
            return ResponseEntity.ok(result);
        }

        long repoCount = repositoryEntityRepository.countByUserIdAndGitProvider(user.getId(), "github");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("connected", true);
        result.put("status", "CONNECTED");
        result.put("repositoryCount", repoCount);
        OAuthAccountEntity oauth = oauthOpt.get();
        result.put("githubUserId", oauth.getProviderUserId());
        result.put("connectedAt", oauth.getCreatedAt() != null ? oauth.getCreatedAt().toString() : null);
        result.put("githubUsername", user.getUsername() != null ? user.getUsername() : user.getEmail().split("@")[0]);
        result.put("avatarUrl", user.getAvatarUrl());
        result.put("lastSyncedAt", java.time.LocalDateTime.now().atOffset(java.time.ZoneOffset.UTC).toString());

        Map<String, Object> ghUserObj = new LinkedHashMap<>();
        ghUserObj.put("id", oauth.getProviderUserId());
        ghUserObj.put("login", user.getUsername());
        ghUserObj.put("avatarUrl", user.getAvatarUrl());

        try {
            Map<String, Object> ghProfile = gitHubClient.getAuthenticatedUserProfile(oauth.getAccessToken().trim());
            if (ghProfile != null) {
                if (ghProfile.get("login") != null) {
                    result.put("githubUsername", ghProfile.get("login"));
                    ghUserObj.put("login", ghProfile.get("login"));
                }
                if (ghProfile.get("avatar_url") != null) {
                    result.put("avatarUrl", ghProfile.get("avatar_url"));
                    ghUserObj.put("avatarUrl", ghProfile.get("avatar_url"));
                }
                if (ghProfile.get("id") != null) {
                    result.put("githubUserId", ghProfile.get("id").toString());
                    ghUserObj.put("id", ghProfile.get("id").toString());
                }
            }
        } catch (Exception ex) {
            log.warn("Could not enrich GitHub connection status with live profile: {}", ex.getMessage());
        }

        result.put("githubUser", ghUserObj);
        return ResponseEntity.ok(result);
    }

    @RequestMapping(value = {"/api/v1/github/connection", "/api/v1/github/disconnect", "/api/v1/auth/disconnect/github"}, method = {RequestMethod.POST, RequestMethod.DELETE})
    public ResponseEntity<Map<String, Object>> disconnectGitHub(Principal principal) {
        long startNs = System.nanoTime();
        String principalName = principal != null ? principal.getName() : null;
        if (principalName == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "code", "UNAUTHORIZED",
                    "message", "Unauthorized"
            ));
        }

        Optional<UserEntity> userOpt = userRepository.findByEmail(principalName)
                .or(() -> userRepository.findByUsername(principalName));
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "code", "USER_NOT_FOUND",
                    "message", "Authenticated user record not found"
            ));
        }

        UserEntity user = userOpt.get();
        Optional<OAuthAccountEntity> oauthOpt = oauthAccountRepository.findByUserAndProvider(user, "github");
        String accessTokenToRevoke = (oauthOpt.isPresent() && oauthOpt.get().getAccessToken() != null)
                ? oauthOpt.get().getAccessToken().trim() : null;

        long dbStartNs = System.nanoTime();
        // Fast synchronous connection state update in database
        transactionTemplate.executeWithoutResult(status -> {
            oauthOpt.ifPresent(oauthAccountRepository::delete);
            if (user.getGithubId() != null) {
                user.setGithubId(null);
                userRepository.save(user);
            }
        });
        double dbMs = (System.nanoTime() - dbStartNs) / 1_000_000.0;

        // Offload remote token revocation & cascading repository cleanup to non-blocking background async task
        double apiMs = 0.0;
        double cleanupMs = 0.0;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            long bgStartNs = System.nanoTime();
            if (accessTokenToRevoke != null && !accessTokenToRevoke.isEmpty()) {
                try {
                    gitHubClient.revokeOAuthToken(accessTokenToRevoke);
                } catch (Exception ex) {
                    log.warn("[GITHUB DISCONNECT] Async token revocation failed: {}", ex.getMessage());
                }
            }
            double bgApiMs = (System.nanoTime() - bgStartNs) / 1_000_000.0;

            long cleanupStartNs = System.nanoTime();
            try {
                List<ai.riskvision.graveyard.entity.RepositoryEntity> userGhRepos =
                        repositoryEntityRepository.findByUserIdAndGitProvider(user.getId(), "github");
                for (ai.riskvision.graveyard.entity.RepositoryEntity repo : userGhRepos) {
                    predictionRepository.deleteByRepositoryId(repo.getId());
                    repoMetricsRepository.deleteByRepositoryId(repo.getId());
                    activityRepository.deleteByRepositoryId(repo.getId());
                }
                repositoryEntityRepository.deleteByUserIdAndGitProvider(user.getId(), "github");
            } catch (Exception ex) {
                log.warn("[GITHUB DISCONNECT] Background repo cleanup error: {}", ex.getMessage());
            }
            double bgCleanupMs = (System.nanoTime() - cleanupStartNs) / 1_000_000.0;
            log.info("[PERF_METRICS] disconnect.background_api_ms={} disconnect.background_cleanup_ms={}",
                    Math.round(bgApiMs * 100.0) / 100.0, Math.round(bgCleanupMs * 100.0) / 100.0);
        });

        double totalMs = (System.nanoTime() - startNs) / 1_000_000.0;
        log.info("[PERF_METRICS] disconnect.request.total_ms={} disconnect.database_ms={} disconnect.github_api_ms={} disconnect.cleanup_ms={}",
                Math.round(totalMs * 100.0) / 100.0, Math.round(dbMs * 100.0) / 100.0, apiMs, cleanupMs);

        log.info("[GITHUB DISCONNECT] Disconnected GitHub account for user {} in {}ms", user.getEmail(), Math.round(totalMs * 100.0) / 100.0);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "connected", false,
                "message", "GitHub account disconnected successfully"
        ));
    }

    @RequestMapping(value = {"/api/v1/github/sync", "/api/github/sync"}, method = {RequestMethod.POST, RequestMethod.GET})
    public ResponseEntity<Map<String, Object>> triggerSync(Principal principal) {
        String principalName = principal != null ? principal.getName() : null;
        if (principalName == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "Unauthorized"
            ));
        }

        Optional<UserEntity> userOpt = userRepository.findByEmail(principalName)
                .or(() -> userRepository.findByUsername(principalName));
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "message", "User not found"
            ));
        }

        UserEntity user = userOpt.get();
        Optional<OAuthAccountEntity> oauthOpt = oauthAccountRepository.findByUserAndProvider(user, "github");
        if (oauthOpt.isEmpty() || oauthOpt.get().getAccessToken() == null || oauthOpt.get().getAccessToken().trim().isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "connected", false,
                    "message", "GitHub account is not connected. Please connect GitHub first."
            ));
        }

        try {
            String token = oauthOpt.get().getAccessToken().trim();
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    repositorySyncService.syncUserGitHubRepositories(user, token);
                } catch (Exception ex) {
                    log.warn("[GITHUB SYNC] Async repo sync error: {}", ex.getMessage());
                }
            });
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "connected", true,
                    "message", "Repository synchronization initiated in background"
            ));
        } catch (Exception ex) {
            log.error("[GITHUB SYNC API] Manual sync failed for user {}: {}", user.getEmail(), ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "connected", true,
                    "message", "Synchronization failed: " + ex.getMessage()
            ));
        }
    }

    @GetMapping({"/api/v1/github/repositories", "/api/v1/github/user-repositories", "/api/v1/github/user/repositories", "/api/v1/github/user/repos"})
    public ResponseEntity<Map<String, Object>> getUserRepositories(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "100") Integer perPage,
            @RequestParam(required = false, defaultValue = "all") String visibility,
            @RequestParam(required = false, defaultValue = "owner,collaborator,organization_member") String affiliation,
            @RequestParam(required = false, defaultValue = "updated") String sort,
            Principal principal) {

        String principalName = principal != null ? principal.getName() : null;
        log.info("[GITHUB AUTH] Repository fetch requested by principal={}", principalName);

        if (principalName == null) {
            return ResponseEntity.ok(Map.of("connected", false, "repositories", Collections.emptyList()));
        }

        Optional<UserEntity> userOpt = userRepository.findByEmail(principalName)
                .or(() -> userRepository.findByUsername(principalName));
        if (userOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of("connected", false, "repositories", Collections.emptyList()));
        }

        UserEntity user = userOpt.get();
        Optional<OAuthAccountEntity> oauthOpt = oauthAccountRepository.findByUserAndProvider(user, "github");
        if (oauthOpt.isEmpty() || oauthOpt.get().getAccessToken() == null || oauthOpt.get().getAccessToken().trim().isEmpty()) {
            log.info("[GITHUB AUTH] No active GitHub OAuth connection for user={}. Returning connected=false", user.getEmail());
            return ResponseEntity.ok(Map.of("connected", false, "repositories", Collections.emptyList()));
        }

        String userToken = oauthOpt.get().getAccessToken().trim();

        try {
            // Trigger background repository sync without blocking the immediate response
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    List<ai.riskvision.graveyard.entity.RepositoryEntity> synced = repositorySyncService.syncUserGitHubRepositories(user, userToken);
                    log.info("[GITHUB SYNC] Background sync complete for user id={}, email={}. Synced {} repos.", user.getId(), user.getEmail(), synced.size());
                } catch (Exception syncEx) {
                    log.warn("[GITHUB SYNC] Background repo sync error: {}", syncEx.getMessage());
                }
            });

            List<Map<String, Object>> rawRepos = gitHubClient.getUserRepositories(userToken, page, perPage, visibility, affiliation, sort);
            log.info("[GITHUB API] GET /user/repos succeeded. Returned {} repositories.", rawRepos != null ? rawRepos.size() : 0);
            List<Map<String, Object>> normalized = new java.util.ArrayList<>();
            if (rawRepos != null) {
                for (Map<String, Object> repo : rawRepos) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", repo.get("id"));
                    item.put("node_id", repo.get("node_id"));
                    item.put("name", repo.get("name"));
                    item.put("full_name", repo.get("full_name"));

                    Object ownerObj = repo.get("owner");
                    if (ownerObj instanceof Map<?, ?> ownerMap) {
                        item.put("owner", ownerMap.get("login"));
                        item.put("owner_avatar_url", ownerMap.get("avatar_url"));
                    } else {
                        item.put("owner", ownerObj);
                        item.put("owner_avatar_url", null);
                    }

                    item.put("html_url", repo.get("html_url"));
                    item.put("clone_url", repo.get("clone_url"));
                    item.put("ssh_url", repo.get("ssh_url"));
                    item.put("default_branch", repo.get("default_branch") != null ? repo.get("default_branch") : "main");
                    item.put("private", repo.get("private"));
                    item.put("visibility", repo.get("visibility") != null ? repo.get("visibility") : (Boolean.TRUE.equals(repo.get("private")) ? "private" : "public"));
                    item.put("description", repo.get("description"));
                    item.put("language", repo.get("language"));
                    item.put("updated_at", repo.get("updated_at"));
                    item.put("stargazers_count", repo.get("stargazers_count"));
                    item.put("forks_count", repo.get("forks_count"));

                    normalized.add(item);
                }
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("connected", true);
            response.put("success", true);
            response.put("repositories", normalized);
            response.put("total", normalized.size());
            response.put("pagination", Map.of(
                    "page", page != null ? page : 1,
                    "per_page", perPage != null ? perPage : 100,
                    "has_next", normalized.size() >= (perPage != null ? perPage : 100)
            ));
            return ResponseEntity.ok(response);
        } catch (GitHubAuthenticationException ex) {
            log.warn("[GITHUB API ERROR] Authentication failed (401): {}", ex.getMessage());
            return ResponseEntity.ok(Map.of(
                    "connected", false,
                    "success", false,
                    "error", Map.of(
                            "code", "GITHUB_AUTHENTICATION_EXPIRED",
                            "message", "GitHub authentication has expired or is invalid. Please reconnect GitHub."
                    ),
                    "repositories", Collections.emptyList()
            ));
        } catch (GitHubRateLimitException ex) {
            log.warn("[GITHUB API ERROR] Rate limit (429): {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "connected", true,
                    "success", false,
                    "error", Map.of(
                            "code", "GITHUB_RATE_LIMIT",
                            "message", "GitHub API rate limit exceeded. Please try again later."
                    ),
                    "repositories", Collections.emptyList()
            ));
        } catch (GitHubResourceNotFoundException ex) {
            log.warn("[GITHUB API ERROR] Not found (404): {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "connected", true,
                    "success", false,
                    "error", Map.of(
                            "code", "GITHUB_RESOURCE_NOT_FOUND",
                            "message", "GitHub account or repository resource not found."
                    ),
                    "repositories", Collections.emptyList()
            ));
        } catch (GitHubApiException ex) {
            log.warn("[GITHUB API ERROR] GitHub API Exception (status={}): {}", ex.getStatus(), ex.getMessage());
            HttpStatus status = ex.getStatus() != null ? ex.getStatus() : HttpStatus.BAD_GATEWAY;
            String code = status == HttpStatus.UNAUTHORIZED ? "GITHUB_AUTHENTICATION_EXPIRED"
                        : status == HttpStatus.FORBIDDEN || status == HttpStatus.TOO_MANY_REQUESTS ? "GITHUB_RATE_LIMIT"
                        : status == HttpStatus.NOT_FOUND ? "GITHUB_RESOURCE_NOT_FOUND"
                        : "GITHUB_SERVICE_ERROR";
            return ResponseEntity.status(status).body(Map.of(
                    "connected", status != HttpStatus.UNAUTHORIZED,
                    "success", false,
                    "error", Map.of(
                            "code", code,
                            "message", "GitHub API Error: " + ex.getMessage()
                    ),
                    "repositories", Collections.emptyList()
            ));
        } catch (Exception ex) {
            log.error("[GITHUB API ERROR] Unexpected failure: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "connected", true,
                    "success", false,
                    "error", Map.of(
                            "code", "INTERNAL_SERVER_ERROR",
                            "message", "Unable to fetch GitHub repositories: " + ex.getMessage()
                    ),
                    "repositories", Collections.emptyList()
            ));
        }
    }

    @GetMapping("/api/v1/github/user/profile/{username}")
    public ResponseEntity<Map<String, Object>> getUserProfileByName(@PathVariable String username) {
        return ResponseEntity.ok(gitHubClient.getUserProfile(username));
    }

    @GetMapping("/api/v1/github/orgs/{org}")
    public ResponseEntity<Map<String, Object>> getOrgDetails(@PathVariable String org) {
        return ResponseEntity.ok(gitHubClient.getOrgDetails(org));
    }

    @GetMapping("/api/v1/github/repos/{owner}/{repo}/webhooks")
    public ResponseEntity<List<Map<String, Object>>> getWebhooks(
            @PathVariable String owner,
            @PathVariable String repo) {
        return ResponseEntity.ok(gitHubClient.getWebhooks(owner, repo));
    }

    @GetMapping("/api/v1/github/repos/{owner}/{repo}/events")
    public ResponseEntity<List<Map<String, Object>>> getEvents(
            @PathVariable String owner,
            @PathVariable String repo) {
        return ResponseEntity.ok(gitHubClient.getEvents(owner, repo));
    }

    @GetMapping("/api/v1/github/repos/{owner}/{repo}/activity")
    public ResponseEntity<List<Map<String, Object>>> getActivityTimeline(
            @PathVariable String owner,
            @PathVariable String repo) {
        return ResponseEntity.ok(gitHubClient.getActivityTimeline(owner, repo));
    }
}
