package ai.riskvision.graveyard.client;

import ai.riskvision.graveyard.config.GitHubProperties;
import ai.riskvision.graveyard.exception.GitHubAuthenticationException;
import ai.riskvision.graveyard.exception.GitHubRateLimitException;
import ai.riskvision.graveyard.exception.GitHubResourceNotFoundException;
import ai.riskvision.graveyard.service.GitHubAuditLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(MockitoExtension.class)
class GitHubClientTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private GitHubProperties properties;

    @Mock
    private GitHubAuditLogger auditLogger;

    private GitHubClient gitHubClient;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);

        properties = new GitHubProperties();
        properties.setToken("test-github-pat-token-12345");

        gitHubClient = new GitHubClient(properties, restTemplate, auditLogger);
    }

    @Test
    void testCreateHeadersIncludesAuthorizationAndVersion() {
        HttpHeaders headers = gitHubClient.createHeaders();
        assertEquals("Bearer test-github-pat-token-12345", headers.getFirst("Authorization"));
        assertEquals("application/vnd.github+json", headers.getFirst("Accept"));
        assertEquals("2022-11-28", headers.getFirst("X-GitHub-Api-Version"));
        assertEquals("RiskVision-AI-Platform", headers.getFirst("User-Agent"));
    }

    @Test
    void testGetRepositoryMetadataSuccess() {
        String jsonResponse = "{\"name\":\"test-repo\",\"stargazers_count\":42,\"open_issues_count\":3}";

        mockServer.expect(requestTo("https://api.github.com/repos/octocat/test-repo"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test-github-pat-token-12345"))
                .andExpect(header("X-GitHub-Api-Version", "2022-11-28"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON)
                        .header("X-RateLimit-Remaining", "4999")
                        .header("X-RateLimit-Reset", "1700000000"));

        Map<String, Object> metadata = gitHubClient.getRepositoryMetadata("octocat", "test-repo");

        assertNotNull(metadata);
        assertEquals("test-repo", metadata.get("name"));
        assertEquals(42, metadata.get("stargazers_count"));
        mockServer.verify();
    }

    @Test
    void test401UnauthorizedThrowsGitHubAuthenticationException() {
        mockServer.expect(requestTo("https://api.github.com/repos/octocat/private-repo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"message\":\"Bad credentials\"}"));

        assertThrows(GitHubAuthenticationException.class, () ->
                gitHubClient.getRepositoryMetadata("octocat", "private-repo")
        );
        mockServer.verify();
    }

    @Test
    void test403RateLimitExceededThrowsGitHubRateLimitException() {
        mockServer.expect(requestTo("https://api.github.com/repos/octocat/test-repo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .header("X-RateLimit-Remaining", "0")
                        .header("X-RateLimit-Reset", "1700000000")
                        .body("{\"message\":\"API rate limit exceeded\"}"));

        GitHubRateLimitException ex = assertThrows(GitHubRateLimitException.class, () ->
                gitHubClient.getRepositoryMetadata("octocat", "test-repo")
        );

        assertEquals(0L, ex.getRateLimitRemaining());
        assertEquals(1700000000L, ex.getRateLimitResetEpochSeconds());
        mockServer.verify();
    }

    @Test
    void test404NotFoundThrowsGitHubResourceNotFoundException() {
        mockServer.expect(requestTo("https://api.github.com/repos/octocat/nonexistent"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("{\"message\":\"Not Found\"}"));

        assertThrows(GitHubResourceNotFoundException.class, () ->
                gitHubClient.getRepositoryMetadata("octocat", "nonexistent")
        );
        mockServer.verify();
    }

    @Test
    void testGetHealthStatusUnauthenticated() {
        mockServer.expect(requestTo("https://api.github.com/user"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        mockServer.expect(requestTo("https://api.github.com/rate_limit"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"rate\":{\"limit\":60,\"remaining\":59}}", MediaType.APPLICATION_JSON));

        Map<String, Object> health = gitHubClient.getHealthStatus();

        assertNotNull(health);
        assertEquals("DOWN", health.get("status"));
        assertEquals(false, health.get("pat_valid"));
        assertTrue((Boolean) health.get("pat_configured"));
    }
}
