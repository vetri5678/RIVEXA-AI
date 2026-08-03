package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.client.GitHubClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class GitHubController {

    private final GitHubClient gitHubClient;

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
