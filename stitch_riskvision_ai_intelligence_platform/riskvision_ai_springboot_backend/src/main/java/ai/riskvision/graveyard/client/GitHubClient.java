package ai.riskvision.graveyard.client;

import ai.riskvision.graveyard.config.GitHubProperties;
import ai.riskvision.graveyard.exception.*;
import ai.riskvision.graveyard.service.GitHubAuditLogger;
import ai.riskvision.graveyard.util.GitHubValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Centralized, production-ready GitHub API Client.
 * All backend services and controllers MUST use this single reusable client.
 * Securely uses GitHub PAT from environment variables (GITHUB_TOKEN / GITHUB_PAT).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GitHubClient {

    private final GitHubProperties gitHubProperties;
    private final RestTemplate restTemplate;
    private final GitHubAuditLogger auditLogger;

    private static final String DEFAULT_USER_AGENT = "RiskVision-AI-Platform";
    private static final String GITHUB_API_VERSION = "2022-11-28";
    private static final String GITHUB_ACCEPT_HEADER = "application/vnd.github+json";

    /**
     * Builds HTTP Headers with injected Authorization PAT token and GitHub API required headers.
     */
    public HttpHeaders createHeaders() {
        return createHeaders(null);
    }

    public HttpHeaders createHeaders(String customToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", GITHUB_ACCEPT_HEADER);
        headers.set("X-GitHub-Api-Version", GITHUB_API_VERSION);
        headers.set("User-Agent", DEFAULT_USER_AGENT);

        String tokenToUse = (customToken != null && !customToken.trim().isEmpty() && !customToken.startsWith("mock-"))
                ? customToken.trim()
                : gitHubProperties.getToken();

        if (tokenToUse != null && !tokenToUse.trim().isEmpty() && !tokenToUse.startsWith("mock-")) {
            headers.set("Authorization", "Bearer " + tokenToUse.trim());
        }
        return headers;
    }

    /**
     * Centralized execution method for GitHub REST API calls.
     */
    public <T> T executeRequest(String endpoint, HttpMethod method, Object requestBody,
                                ParameterizedTypeReference<T> responseType,
                                String owner, String repo, String action, String description) {
        return executeRequest(endpoint, method, requestBody, responseType, owner, repo, action, description, null);
    }

    public <T> T executeRequest(String endpoint, HttpMethod method, Object requestBody,
                                ParameterizedTypeReference<T> responseType,
                                String owner, String repo, String action, String description,
                                String customToken) {
        String baseUrl = gitHubProperties.getApi().getBaseUrl();
        String fullUrl = endpoint.startsWith("http") ? endpoint : (baseUrl + (endpoint.startsWith("/") ? endpoint : "/" + endpoint));
        HttpHeaders headers = createHeaders(customToken);
        HttpEntity<Object> entity = new HttpEntity<>(requestBody, headers);

        long startTime = System.currentTimeMillis();
        int statusCode = 200;
        Long remainingLimit = null;
        Long resetTime = null;

        try {
            ResponseEntity<T> response = restTemplate.exchange(fullUrl, method, entity, responseType);
            long executionTime = System.currentTimeMillis() - startTime;
            statusCode = response.getStatusCode().value();
            boolean success = response.getStatusCode().is2xxSuccessful();

            if (response.getHeaders().getFirst("X-RateLimit-Remaining") != null) {
                try {
                    remainingLimit = Long.parseLong(Objects.requireNonNull(response.getHeaders().getFirst("X-RateLimit-Remaining")));
                } catch (Exception ignored) {}
            }
            if (response.getHeaders().getFirst("X-RateLimit-Reset") != null) {
                try {
                    resetTime = Long.parseLong(Objects.requireNonNull(response.getHeaders().getFirst("X-RateLimit-Reset")));
                } catch (Exception ignored) {}
            }

            auditLogger.logRequest(owner, repo, endpoint, method.name(), executionTime, statusCode,
                    remainingLimit, success, "SYSTEM", "127.0.0.1", DEFAULT_USER_AGENT, action, description);

            return response.getBody();
        } catch (HttpClientErrorException ex) {
            long executionTime = System.currentTimeMillis() - startTime;
            statusCode = ex.getStatusCode().value();

            if (ex.getResponseHeaders() != null) {
                if (ex.getResponseHeaders().getFirst("X-RateLimit-Remaining") != null) {
                    try {
                        remainingLimit = Long.parseLong(Objects.requireNonNull(ex.getResponseHeaders().getFirst("X-RateLimit-Remaining")));
                    } catch (Exception ignored) {}
                }
                if (ex.getResponseHeaders().getFirst("X-RateLimit-Reset") != null) {
                    try {
                        resetTime = Long.parseLong(Objects.requireNonNull(ex.getResponseHeaders().getFirst("X-RateLimit-Reset")));
                    } catch (Exception ignored) {}
                }
            }

            auditLogger.logRequest(owner, repo, endpoint, method.name(), executionTime, statusCode,
                    remainingLimit, false, "SYSTEM", "127.0.0.1", DEFAULT_USER_AGENT, action, ex.getMessage());

            handleHttpError(statusCode, endpoint, ex.getResponseBodyAsString(), remainingLimit, resetTime);
            throw new GitHubApiException("GitHub API error: " + ex.getMessage(), HttpStatus.valueOf(ex.getStatusCode().value()), endpoint, ex.getResponseBodyAsString());
        } catch (HttpServerErrorException ex) {
            long executionTime = System.currentTimeMillis() - startTime;
            statusCode = ex.getStatusCode().value();

            auditLogger.logRequest(owner, repo, endpoint, method.name(), executionTime, statusCode,
                    null, false, "SYSTEM", "127.0.0.1", DEFAULT_USER_AGENT, action, ex.getMessage());

            throw new GitHubApiException("GitHub Server Error (5xx): " + ex.getMessage(), HttpStatus.valueOf(ex.getStatusCode().value()), endpoint, ex.getResponseBodyAsString());
        } catch (Exception ex) {
            long executionTime = System.currentTimeMillis() - startTime;
            auditLogger.logRequest(owner, repo, endpoint, method.name(), executionTime, 500,
                    null, false, "SYSTEM", "127.0.0.1", DEFAULT_USER_AGENT, action, ex.getMessage());

            throw new GitHubApiException("Unexpected GitHub API client error: " + ex.getMessage(), ex);
        }
    }

    private void handleHttpError(int statusCode, String endpoint, String responseBody, Long remainingLimit, Long resetTime) {
        if (statusCode == 401) {
            throw new GitHubAuthenticationException("401 Unauthorized: Invalid or expired GitHub Personal Access Token (PAT).", endpoint, responseBody);
        } else if (statusCode == 403 || statusCode == 429) {
            throw new GitHubRateLimitException("GitHub API Rate Limit Exceeded or Access Forbidden (403/429).", remainingLimit, resetTime, endpoint, responseBody);
        } else if (statusCode == 404) {
            throw new GitHubResourceNotFoundException("404 Not Found: GitHub repository or resource not found.", endpoint, responseBody);
        } else if (statusCode == 409) {
            throw new GitHubEmptyRepositoryException("409 Conflict: GitHub repository is empty.", endpoint, responseBody);
        } else if (statusCode == 422) {
            throw new GitHubValidationException("422 Unprocessable Entity: Invalid GitHub repository parameters.", endpoint, responseBody);
        }
    }

    // ─── HEALTH & RATE LIMIT ───────────────────────────────────────────────────

    public Map<String, Object> getHealthStatus() {
        Map<String, Object> health = new LinkedHashMap<>();
        String patToken = gitHubProperties.getToken();
        boolean patConfigured = patToken != null && !patToken.trim().isEmpty() && !patToken.startsWith("mock-");

        health.put("pat_configured", patConfigured);
        health.put("token_masked", patConfigured ? maskToken(patToken) : "NONE");
        health.put("api_base_url", gitHubProperties.getApi().getBaseUrl());

        try {
            Map<String, Object> userProfile = getAuthenticatedUserProfile(patToken);
            health.put("authenticated_user", userProfile.get("login"));
            health.put("user_type", userProfile.get("type"));
            health.put("pat_valid", true);
            health.put("status", "UP");
        } catch (GitHubAuthenticationException ex) {
            health.put("pat_valid", false);
            health.put("status", "DOWN");
            health.put("error", "PAT Token is invalid or unauthenticated.");
        } catch (Exception ex) {
            health.put("pat_valid", false);
            health.put("status", "DEGRADED");
            health.put("error", ex.getMessage());
        }

        try {
            Map<String, Object> rateLimit = getRateLimit();
            health.put("rate_limit", rateLimit.get("rate"));
        } catch (Exception ex) {
            health.put("rate_limit", "Unavailable");
        }

        health.put("timestamp", new Date().toString());
        return health;
    }

    public Map<String, Object> getRateLimit() {
        return executeRequest("/rate_limit", HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {}, null, null,
                "GITHUB_RATE_LIMIT", "Fetch GitHub API Rate Limit Info");
    }

    // ─── REPOSITORY METADATA & STATS ──────────────────────────────────────────

    @Cacheable(value = "githubMetadata", key = "#owner + '/' + #repo", unless = "#result == null")
    public Map<String, Object> getRepositoryMetadata(String owner, String repo) {
        GitHubValidator.validateOwnerAndRepo(owner, repo);
        return executeRequest("/repos/" + owner + "/" + repo, HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {}, owner, repo,
                "GITHUB_REPO_METADATA", "Fetch repository metadata for " + owner + "/" + repo);
    }

    public Map<String, Object> getRepositoryStats(String owner, String repo) {
        GitHubValidator.validateOwnerAndRepo(owner, repo);
        return executeRequest("/repos/" + owner + "/" + repo + "/stats/participation", HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {}, owner, repo,
                "GITHUB_REPO_STATS", "Fetch repository statistics for " + owner + "/" + repo);
    }

    @Cacheable(value = "githubLanguages", key = "#owner + '/' + #repo", unless = "#result == null")
    public Map<String, Object> getLanguages(String owner, String repo) {
        GitHubValidator.validateOwnerAndRepo(owner, repo);
        return executeRequest("/repos/" + owner + "/" + repo + "/languages", HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {}, owner, repo,
                "GITHUB_REPO_LANGUAGES", "Fetch programming languages breakdown for " + owner + "/" + repo);
    }

    @Cacheable(value = "githubBranches", key = "#owner + '/' + #repo", unless = "#result == null")
    public List<Map<String, Object>> getBranches(String owner, String repo) {
        GitHubValidator.validateOwnerAndRepo(owner, repo);
        return executeRequest("/repos/" + owner + "/" + repo + "/branches?per_page=100", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}, owner, repo,
                "GITHUB_REPO_BRANCHES", "Fetch branches list for " + owner + "/" + repo);
    }

    @Cacheable(value = "githubCommits", key = "#owner + '/' + #repo + ':' + #branch + ':' + #page", unless = "#result == null")
    public List<Map<String, Object>> getCommits(String owner, String repo, String branch, Integer page, Integer perPage) {
        GitHubValidator.validateOwnerAndRepo(owner, repo);
        int p = (page != null && page > 0) ? page : 1;
        int pp = (perPage != null && perPage > 0 && perPage <= 100) ? perPage : 30;
        String branchQuery = (branch != null && !branch.trim().isEmpty()) ? "&sha=" + branch.trim() : "";

        return executeRequest("/repos/" + owner + "/" + repo + "/commits?page=" + p + "&per_page=" + pp + branchQuery,
                HttpMethod.GET, null, new ParameterizedTypeReference<List<Map<String, Object>>>() {}, owner, repo,
                "GITHUB_REPO_COMMITS", "Fetch commit history for " + owner + "/" + repo);
    }

    public Map<String, Object> getLatestCommit(String owner, String repo) {
        List<Map<String, Object>> commits = getCommits(owner, repo, null, 1, 1);
        if (commits != null && !commits.isEmpty()) {
            return commits.get(0);
        }
        return Collections.emptyMap();
    }

    @Cacheable(value = "githubContributors", key = "#owner + '/' + #repo", unless = "#result == null")
    public List<Map<String, Object>> getContributors(String owner, String repo) {
        GitHubValidator.validateOwnerAndRepo(owner, repo);
        return executeRequest("/repos/" + owner + "/" + repo + "/contributors?per_page=100", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}, owner, repo,
                "GITHUB_REPO_CONTRIBUTORS", "Fetch contributors list for " + owner + "/" + repo);
    }

    public List<Map<String, Object>> getPullRequests(String owner, String repo, String state) {
        GitHubValidator.validateOwnerAndRepo(owner, repo);
        String s = (state != null && !state.trim().isEmpty()) ? state.trim() : "open";
        return executeRequest("/repos/" + owner + "/" + repo + "/pulls?state=" + s + "&per_page=100", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}, owner, repo,
                "GITHUB_REPO_PRS", "Fetch pull requests for " + owner + "/" + repo);
    }

    public List<Map<String, Object>> getIssues(String owner, String repo, String state) {
        GitHubValidator.validateOwnerAndRepo(owner, repo);
        String s = (state != null && !state.trim().isEmpty()) ? state.trim() : "open";
        return executeRequest("/repos/" + owner + "/" + repo + "/issues?state=" + s + "&per_page=100", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}, owner, repo,
                "GITHUB_REPO_ISSUES", "Fetch repository issues for " + owner + "/" + repo);
    }

    public List<Map<String, Object>> getReleases(String owner, String repo) {
        GitHubValidator.validateOwnerAndRepo(owner, repo);
        return executeRequest("/repos/" + owner + "/" + repo + "/releases?per_page=50", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}, owner, repo,
                "GITHUB_REPO_RELEASES", "Fetch releases for " + owner + "/" + repo);
    }

    public List<Map<String, Object>> getTags(String owner, String repo) {
        GitHubValidator.validateOwnerAndRepo(owner, repo);
        return executeRequest("/repos/" + owner + "/" + repo + "/tags?per_page=50", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}, owner, repo,
                "GITHUB_REPO_TAGS", "Fetch tags for " + owner + "/" + repo);
    }

    public Map<String, Object> getWorkflows(String owner, String repo) {
        GitHubValidator.validateOwnerAndRepo(owner, repo);
        return executeRequest("/repos/" + owner + "/" + repo + "/actions/workflows", HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {}, owner, repo,
                "GITHUB_REPO_WORKFLOWS", "Fetch GitHub Workflows for " + owner + "/" + repo);
    }

    public List<Map<String, Object>> getDependabotAlerts(String owner, String repo) {
        GitHubValidator.validateOwnerAndRepo(owner, repo);
        return executeRequest("/repos/" + owner + "/" + repo + "/dependabot/alerts?per_page=50", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}, owner, repo,
                "GITHUB_SECURITY_DEPENDABOT", "Fetch Dependabot security alerts for " + owner + "/" + repo);
    }

    public List<Map<String, Object>> getCodeScanningAlerts(String owner, String repo) {
        GitHubValidator.validateOwnerAndRepo(owner, repo);
        return executeRequest("/repos/" + owner + "/" + repo + "/code-scanning/alerts?per_page=50", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}, owner, repo,
                "GITHUB_SECURITY_CODE_SCANNING", "Fetch code scanning alerts for " + owner + "/" + repo);
    }

    public List<Map<String, Object>> getSecretScanningAlerts(String owner, String repo) {
        GitHubValidator.validateOwnerAndRepo(owner, repo);
        return executeRequest("/repos/" + owner + "/" + repo + "/secret-scanning/alerts?per_page=50", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}, owner, repo,
                "GITHUB_SECURITY_SECRET_SCANNING", "Fetch secret scanning alerts for " + owner + "/" + repo);
    }

    public Map<String, Object> getTopics(String owner, String repo) {
        GitHubValidator.validateOwnerAndRepo(owner, repo);
        return executeRequest("/repos/" + owner + "/" + repo + "/topics", HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {}, owner, repo,
                "GITHUB_REPO_TOPICS", "Fetch repository topics for " + owner + "/" + repo);
    }

    public Map<String, Object> getLicense(String owner, String repo) {
        GitHubValidator.validateOwnerAndRepo(owner, repo);
        return executeRequest("/repos/" + owner + "/" + repo + "/license", HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {}, owner, repo,
                "GITHUB_REPO_LICENSE", "Fetch license info for " + owner + "/" + repo);
    }

    @Cacheable(value = "githubReadme", key = "#owner + '/' + #repo", unless = "#result == null")
    public Map<String, Object> getReadme(String owner, String repo) {
        GitHubValidator.validateOwnerAndRepo(owner, repo);
        return executeRequest("/repos/" + owner + "/" + repo + "/readme", HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {}, owner, repo,
                "GITHUB_REPO_README", "Fetch README file for " + owner + "/" + repo);
    }

    public Map<String, Object> getTree(String owner, String repo, String treeSha, Boolean recursive) {
        GitHubValidator.validateOwnerAndRepo(owner, repo);
        String sha = (treeSha != null && !treeSha.trim().isEmpty()) ? treeSha.trim() : "main";
        String rec = Boolean.TRUE.equals(recursive) ? "?recursive=1" : "";
        return executeRequest("/repos/" + owner + "/" + repo + "/git/trees/" + sha + rec, HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {}, owner, repo,
                "GITHUB_REPO_TREE", "Fetch repository tree for " + owner + "/" + repo);
    }

    public Map<String, Object> getFileContent(String owner, String repo, String path) {
        GitHubValidator.validateOwnerAndRepo(owner, repo);
        String p = (path != null && !path.trim().isEmpty()) ? path.trim() : "";
        return executeRequest("/repos/" + owner + "/" + repo + "/contents/" + p, HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {}, owner, repo,
                "GITHUB_FILE_CONTENT", "Fetch file content for " + owner + "/" + repo + " at " + p);
    }

    // ─── SEARCH & USERS / ORGS ───────────────────────────────────────────────

    public Map<String, Object> searchRepositories(String query) {
        return executeRequest("/search/repositories?q=" + query, HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {}, null, null,
                "GITHUB_SEARCH_REPOS", "Search repositories for query: " + query);
    }

    public Map<String, Object> searchCode(String query) {
        return executeRequest("/search/code?q=" + query, HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {}, null, null,
                "GITHUB_SEARCH_CODE", "Search code for query: " + query);
    }

    public Map<String, Object> searchUsers(String query) {
        return executeRequest("/search/users?q=" + query, HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {}, null, null,
                "GITHUB_SEARCH_USERS", "Search users for query: " + query);
    }

    public Map<String, Object> searchOrganizations(String query) {
        return executeRequest("/search/users?q=" + query + "+type:org", HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {}, null, null,
                "GITHUB_SEARCH_ORGS", "Search orgs for query: " + query);
    }

    public Map<String, Object> searchCommits(String query) {
        return executeRequest("/search/commits?q=" + query, HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {}, null, null,
                "GITHUB_SEARCH_COMMITS", "Search commits for query: " + query);
    }

    @Cacheable(value = "githubUserProfile", key = "'me'", unless = "#result == null")
    public Map<String, Object> getUserProfile() {
        return getAuthenticatedUserProfile(null);
    }

    public Map<String, Object> getAuthenticatedUserProfile(String customToken) {
        if (customToken == null || customToken.trim().isEmpty() || customToken.startsWith("mock-")) {
            throw new GitHubAuthenticationException("401 Unauthorized: GitHub account is not connected or token is missing.", "/user", "Disconnected");
        }
        return executeRequest("/user", HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {}, null, null,
                "GITHUB_USER_PROFILE", "Fetch authenticated GitHub user profile", customToken);
    }

    public List<Map<String, Object>> getUserRepositories(Integer page, Integer perPage, String visibility, String affiliation, String sort) {
        return getUserRepositories(null, page, perPage, visibility, affiliation, sort);
    }

    public List<Map<String, Object>> getUserRepositories(String customToken, Integer page, Integer perPage, String visibility, String affiliation, String sort) {
        if (customToken == null || customToken.trim().isEmpty() || customToken.startsWith("mock-")) {
            throw new GitHubAuthenticationException("401 Unauthorized: GitHub account is not connected or token is missing.", "/user/repos", "Disconnected");
        }
        int p = (page != null && page > 0) ? page : 1;
        int pp = (perPage != null && perPage > 0 && perPage <= 100) ? perPage : 100;
        String vis = (visibility != null && !visibility.trim().isEmpty()) ? visibility.trim() : "all";
        String aff = (affiliation != null && !affiliation.trim().isEmpty()) ? affiliation.trim() : "owner,collaborator,organization_member";
        String s = (sort != null && !sort.trim().isEmpty()) ? sort.trim() : "updated";

        String endpoint = "/user/repos?page=" + p + "&per_page=" + pp + "&visibility=" + vis + "&affiliation=" + aff + "&sort=" + s;
        try {
            return executeRequest(endpoint, HttpMethod.GET, null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}, null, null,
                    "GITHUB_USER_REPOS", "Fetch authenticated user GitHub repositories", customToken);
        } catch (Exception ex) {
            log.warn("GET /user/repos failed: {}. Trying user profile fallback.", ex.getMessage());
            try {
                Map<String, Object> profile = getAuthenticatedUserProfile(customToken);
                if (profile != null && profile.get("login") != null) {
                    String username = (String) profile.get("login");
                    return executeRequest("/users/" + username + "/repos?page=" + p + "&per_page=" + pp + "&sort=" + s,
                            HttpMethod.GET, null,
                            new ParameterizedTypeReference<List<Map<String, Object>>>() {}, null, null,
                            "GITHUB_USER_REPOS_BY_NAME", "Fetch public GitHub repositories for user: " + username, customToken);
                }
            } catch (Exception fallbackEx) {
                log.error("Fallback user repository fetch also failed: {}", fallbackEx.getMessage());
            }
            throw ex;
        }
    }

    public Map<String, Object> getUserProfile(String username) {
        return executeRequest("/users/" + username, HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {}, null, null,
                "GITHUB_USER_PROFILE_BY_NAME", "Fetch user profile for: " + username);
    }

    public Map<String, Object> getOrgDetails(String org) {
        return executeRequest("/orgs/" + org, HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {}, null, null,
                "GITHUB_ORG_DETAILS", "Fetch org details for: " + org);
    }

    public List<Map<String, Object>> getWebhooks(String owner, String repo) {
        GitHubValidator.validateOwnerAndRepo(owner, repo);
        return executeRequest("/repos/" + owner + "/" + repo + "/hooks", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}, owner, repo,
                "GITHUB_REPO_WEBHOOKS", "Fetch webhooks for " + owner + "/" + repo);
    }

    public List<Map<String, Object>> getEvents(String owner, String repo) {
        GitHubValidator.validateOwnerAndRepo(owner, repo);
        return executeRequest("/repos/" + owner + "/" + repo + "/events", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}, owner, repo,
                "GITHUB_REPO_EVENTS", "Fetch events timeline for " + owner + "/" + repo);
    }

    public List<Map<String, Object>> getActivityTimeline(String owner, String repo) {
        GitHubValidator.validateOwnerAndRepo(owner, repo);
        return executeRequest("/repos/" + owner + "/" + repo + "/activity", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}, owner, repo,
                "GITHUB_REPO_ACTIVITY", "Fetch activity timeline for " + owner + "/" + repo);
    }

    public void revokeOAuthToken(String userToken) {
        if (userToken == null || userToken.trim().isEmpty()) {
            return;
        }
        String clientId = gitHubProperties.getClientId();
        String clientSecret = gitHubProperties.getClientSecret();
        if (clientId == null || clientId.trim().isEmpty() || clientSecret == null || clientSecret.trim().isEmpty()) {
            log.debug("GitHub OAuth clientId/clientSecret not configured. Skipping remote token revocation.");
            return;
        }

        try {
            String url = gitHubProperties.getApi().getBaseUrl() + "/applications/" + clientId.trim() + "/token";
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(clientId.trim(), clientSecret.trim());
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = Map.of("access_token", userToken.trim());
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(body, headers);

            restTemplate.exchange(url, HttpMethod.DELETE, requestEntity, Void.class);
            log.info("[GITHUB OAUTH] Successfully revoked GitHub OAuth token remotely");
        } catch (Exception ex) {
            log.warn("[GITHUB OAUTH] Remote token revocation attempted but failed: {}", ex.getMessage());
        }
    }

    private String maskToken(String token) {
        if (token == null || token.length() <= 8) return "****";
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }
}
