package ai.riskvision.graveyard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class GitHubAuditLogger {

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public void logRequest(String owner, String repo, String endpoint, String httpMethod,
                           long executionTimeMs, int statusCode, Long rateLimitRemaining,
                           boolean success, String username, String ipAddress,
                           String userAgent, String action, String description) {
        try {
            Map<String, Object> metaMap = new LinkedHashMap<>();
            metaMap.put("owner", owner);
            metaMap.put("repository", repo);
            metaMap.put("endpoint", endpoint);
            metaMap.put("http_method", httpMethod);
            metaMap.put("execution_time_ms", executionTimeMs);
            metaMap.put("status_code", statusCode);
            metaMap.put("rate_limit_remaining", rateLimitRemaining);
            metaMap.put("user_agent", userAgent);

            // Parse OS and Browser basic info from User-Agent
            String os = parseOs(userAgent);
            String browser = parseBrowser(userAgent);
            metaMap.put("operating_system", os);
            metaMap.put("browser", browser);

            String jsonMeta = objectMapper.writeValueAsString(metaMap);

            String statusStr = success ? "success" : "failed";
            String severity = success ? "LOW" : (statusCode == 401 || statusCode == 403 || statusCode == 429 ? "HIGH" : "MEDIUM");

            auditLogService.recordEvent(
                    action != null ? action : "GITHUB_API_REQUEST",
                    "GITHUB",
                    severity,
                    statusStr,
                    description != null ? description : ("GitHub API Call to " + endpoint + " (" + statusCode + ")"),
                    username,
                    ipAddress,
                    endpoint,
                    httpMethod,
                    statusCode,
                    executionTimeMs,
                    jsonMeta
            );

            log.info("[GitHub Audit] action={} endpoint={} statusCode={} timeMs={} rateRemaining={}",
                    action, endpoint, statusCode, executionTimeMs, rateLimitRemaining);
        } catch (Exception e) {
            log.warn("Failed to record GitHub audit log: {}", e.getMessage());
        }
    }

    private String parseOs(String userAgent) {
        if (userAgent == null) return "Unknown";
        if (userAgent.contains("Windows")) return "Windows";
        if (userAgent.contains("Mac")) return "macOS";
        if (userAgent.contains("Linux")) return "Linux";
        if (userAgent.contains("Android")) return "Android";
        if (userAgent.contains("iPhone") || userAgent.contains("iPad")) return "iOS";
        return "Other";
    }

    private String parseBrowser(String userAgent) {
        if (userAgent == null) return "Unknown";
        if (userAgent.contains("Edg")) return "Edge";
        if (userAgent.contains("Chrome")) return "Chrome";
        if (userAgent.contains("Safari") && !userAgent.contains("Chrome")) return "Safari";
        if (userAgent.contains("Firefox")) return "Firefox";
        return "Other";
    }
}
