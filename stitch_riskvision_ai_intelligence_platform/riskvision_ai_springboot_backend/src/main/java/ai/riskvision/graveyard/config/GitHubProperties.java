package ai.riskvision.graveyard.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "github")
public class GitHubProperties {

    /**
     * GitHub Personal Access Token (PAT) read from GITHUB_TOKEN or GITHUB_PAT environment variables.
     */
    private String token;

    /**
     * OAuth Client ID (optional, used for OAuth token revocation).
     */
    private String clientId;

    /**
     * OAuth Client Secret (optional, used for OAuth token revocation).
     */
    private String clientSecret;

    private Api api = new Api();

    @Data
    public static class Api {
        private String baseUrl = "https://api.github.com";
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 10000;
        private int maxConnTotal = 50;
        private int maxConnPerRoute = 20;
        private int cacheTtlMinutes = 15;
        private int maxFilesToAnalyze = 100;
        private long maxFileSizeBytes = 512000L; // 500 KB
    }
}
