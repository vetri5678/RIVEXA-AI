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
    private final ai.riskvision.graveyard.service.RepositorySyncService repositorySyncService;
    private final ai.riskvision.graveyard.service.RepoPredictionService predictionService;
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
            return ResponseEntity.ok(Map.of(
                    "connected", false,
                    "status", "DISCONNECTED",
                    "repositoryCount", 0
            ));
        }

        Optional<UserEntity> userOpt = userRepository.findByEmail(principalName)
                .or(() -> userRepository.findByUsername(principalName));
        if (userOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "connected", false,
                    "status", "DISCONNECTED",
                    "repositoryCount", 0
            ));
        }

        UserEntity user = userOpt.get();
        Optional<OAuthAccountEntity> oauthOpt = oauthAccountRepository.findByUserAndProvider(user, "github");
        long repoCount = repositoryEntityRepository.countByUserIdAndGitProvider(user.getId(), "github");
        if (repoCount == 0) {
            repoCount = repositoryEntityRepository.countByUserId(user.getId());
        }

        boolean hasOAuthToken = oauthOpt.isPresent() && oauthOpt.get().getAccessToken() != null && !oauthOpt.get().getAccessToken().trim().isEmpty();
        boolean isConnected = hasOAuthToken || repoCount > 0;

        if (!isConnected) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("connected", false);
            result.put("status", "DISCONNECTED");
            result.put("repositoryCount", 0);
            return ResponseEntity.ok(result);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("connected", true);
        result.put("status", "CONNECTED");
        result.put("repositoryCount", repoCount);
        if (oauthOpt.isPresent()) {
            OAuthAccountEntity oauth = oauthOpt.get();
            result.put("githubUserId", oauth.getProviderUserId());
            result.put("connectedAt", oauth.getCreatedAt() != null ? oauth.getCreatedAt().toString() : null);
        } else {
            result.put("githubUserId", user.getGithubId());
            result.put("connectedAt", null);
        }
        result.put("githubUsername", user.getUsername() != null ? user.getUsername() : user.getEmail().split("@")[0]);
        result.put("avatarUrl", user.getAvatarUrl());
        result.put("lastSyncedAt", java.time.LocalDateTime.now().atOffset(java.time.ZoneOffset.UTC).toString());

        if (hasOAuthToken) {
            try {
                Map<String, Object> ghProfile = gitHubClient.getAuthenticatedUserProfile(oauthOpt.get().getAccessToken().trim());
                if (ghProfile != null) {
                    if (ghProfile.get("login") != null) result.put("githubUsername", ghProfile.get("login"));
                    if (ghProfile.get("avatar_url") != null) result.put("avatarUrl", ghProfile.get("avatar_url"));
                    if (ghProfile.get("id") != null) result.put("githubUserId", ghProfile.get("id").toString());
                }
            } catch (Exception ex) {
                log.warn("Could not enrich GitHub connection status with live profile: {}", ex.getMessage());
            }
        }

        return ResponseEntity.ok(result);
    }

    @RequestMapping(value = {"/api/v1/github/disconnect", "/api/v1/auth/disconnect/github"}, method = {RequestMethod.POST, RequestMethod.DELETE})
    public ResponseEntity<Map<String, Object>> disconnectGitHub(Principal principal) {
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
        if (oauthOpt.isEmpty()) {
            log.info("[GITHUB DISCONNECT] User {} requested GitHub disconnect but no connection exists", user.getEmail());
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "code", "GITHUB_NOT_CONNECTED",
                    "message", "No GitHub account is currently connected",
                    "connected", false
            ));
        }

        OAuthAccountEntity oauth = oauthOpt.get();
        String tokenToRevoke = oauth.getAccessToken();

        // Perform best-effort remote OAuth token revocation (OUTSIDE DB transaction boundary)
        if (tokenToRevoke != null && !tokenToRevoke.trim().isEmpty()) {
            try {
                gitHubClient.revokeOAuthToken(tokenToRevoke.trim());
            } catch (Exception ex) {
                log.warn("[GITHUB DISCONNECT] Token revocation failed: {}", ex.getMessage());
            }
        }

        // Perform DB deletions inside programmatic transaction template
        transactionTemplate.executeWithoutResult(status -> {
            oauthAccountRepository.delete(oauth);
            if (user.getGithubId() != null) {
                user.setGithubId(null);
                userRepository.save(user);
            }
            try {
                repositoryEntityRepository.deleteByUserIdAndGitProvider(user.getId(), "github");
            } catch (Exception ex) {
                log.warn("[GITHUB DISCONNECT] Could not delete cached repositories for user {}: {}", user.getEmail(), ex.getMessage());
            }
        });

        log.info("[GITHUB DISCONNECT] Disconnected GitHub account (providerUserId={}) for user {}", oauth.getProviderUserId(), user.getEmail());

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
            List<ai.riskvision.graveyard.entity.RepositoryEntity> synced = repositorySyncService.syncUserGitHubRepositories(user, token);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "connected", true,
                    "message", "Synchronization complete",
                    "syncedCount", synced.size()
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
            // Synchronize and persist user repositories into DB for user isolation and dashboard metrics
            try {
                List<ai.riskvision.graveyard.entity.RepositoryEntity> synced = repositorySyncService.syncUserGitHubRepositories(user, userToken);
                log.info("[GITHUB SYNC] Synchronized {} repositories to database for user id={}, email={}", synced.size(), user.getId(), user.getEmail());

                // Auto-run initial prediction for newly synchronized repositories
                for (ai.riskvision.graveyard.entity.RepositoryEntity repo : synced) {
                    if ("PENDING".equalsIgnoreCase(repo.getPredictionStatus()) || repo.getFailureProbability() == null || repo.getFailureProbability() == 0.0) {
                        try {
                            predictionService.runPrediction(repo.getId(), user.getEmail());
                        } catch (Exception predEx) {
                            log.debug("[GITHUB SYNC] Initial prediction skipped for repo {}: {}", repo.getRepositoryName(), predEx.getMessage());
                        }
                    }
                }
            } catch (Exception syncEx) {
                log.warn("[GITHUB SYNC] Synchronizing repositories to DB encountered error: {}", syncEx.getMessage());
            }

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
