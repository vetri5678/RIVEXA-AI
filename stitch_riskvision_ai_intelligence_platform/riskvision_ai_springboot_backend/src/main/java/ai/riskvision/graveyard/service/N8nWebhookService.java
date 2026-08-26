package ai.riskvision.graveyard.service;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * N8nWebhookService — Asynchronous, resilient webhook dispatcher for external
 * automation engines (e.g. n8n).
 * 
 * Ensures external integration failures (timeouts, HTTP 500, network errors, missing URLs)
 * never break core business logic (auth, predictions, repository analysis).
 */
@Service
@Slf4j
public class N8nWebhookService {

    @Setter
    private RestTemplate restTemplate;

    @Value("${n8n.webhook.enabled:true}")
    @Setter
    private boolean webhookEnabled = true;

    @Value("${n8n.webhook.connect-timeout-ms:2000}")
    @Setter
    private int connectTimeoutMs = 2000;

    @Value("${n8n.webhook.read-timeout-ms:3000}")
    @Setter
    private int readTimeoutMs = 3000;

    @Value("${n8n.webhook.max-retries:2}")
    @Setter
    private int maxRetries = 2;

    @Value("${n8n.webhook.registration:http://localhost:5678/webhook/registration-verification}")
    @Setter
    private String registrationWebhookUrl;

    @Value("${n8n.webhook.login-success:http://localhost:5678/webhook/login-success}")
    @Setter
    private String loginSuccessWebhookUrl;

    @Value("${n8n.webhook.login-failed:http://localhost:5678/webhook/login-failed-warning}")
    @Setter
    private String loginFailedWebhookUrl;

    @Value("${n8n.webhook.password-reset:http://localhost:5678/webhook/password-reset}")
    @Setter
    private String passwordResetWebhookUrl;

    @Value("${n8n.webhook.password-changed:http://localhost:5678/webhook/password-changed}")
    @Setter
    private String passwordChangedWebhookUrl;

    @Value("${n8n.webhook.account-locked:http://localhost:5678/webhook/account-locked}")
    @Setter
    private String accountLockedWebhookUrl;

    @Value("${n8n.webhook.oauth-linked:http://localhost:5678/webhook/oauth-linked}")
    @Setter
    private String oauthLinkedWebhookUrl;

    @Value("${n8n.webhook.prediction-completed:http://localhost:5678/webhook/prediction-completed}")
    @Setter
    private String predictionCompletedWebhookUrl;

    @Value("${n8n.webhook.repository-sync:http://localhost:5678/webhook/repository-sync}")
    @Setter
    private String repositorySyncWebhookUrl;

    @Value("${n8n.webhook.high-risk-detected:http://localhost:5678/webhook/high-risk-detected}")
    @Setter
    private String highRiskDetectedWebhookUrl;

    @Value("${n8n.webhook.report-generated:http://localhost:5678/webhook/report-generated}")
    @Setter
    private String reportGeneratedWebhookUrl;

    @Value("${n8n.webhook.risk-threshold:80.0}")
    @Setter
    private double riskAlertThreshold = 80.0;

    @Value("${n8n.webhook.secret:rv_n8n_secret_key_2026}")
    @Setter
    private String webhookSecret;

    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    private volatile LocalDateTime lastSuccessfulWebhook;
    private volatile LocalDateTime lastFailedWebhook;
    private volatile String lastError;

    public N8nWebhookService() {
        // Default constructor for Spring
    }

    public N8nWebhookService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    public void init() {
        if (this.restTemplate == null) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(connectTimeoutMs);
            factory.setReadTimeout(readTimeoutMs);
            this.restTemplate = new RestTemplate(factory);
        }
    }

    private RestTemplate getOrCreateRestTemplate() {
        if (this.restTemplate == null) {
            init();
        }
        return this.restTemplate;
    }

    private String computeSignature(String timestamp, String requestId, String data) {
        try {
            String secret = (webhookSecret != null && !webhookSecret.isEmpty()) ? webhookSecret : "rv_n8n_secret_key_2026";
            String rawData = timestamp + "." + requestId + "." + data;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(rawData.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : rawHmac) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String computeSignature(String data) {
        return computeSignature(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME), "legacy-req", data);
    }

    /**
     * Dispatch webhook with timeout, retries, security headers, and non-blocking failure safety.
     */
    public boolean sendWebhook(String url, Map<String, Object> payload, String eventName) {
        if (!webhookEnabled) {
            log.info("[WEBHOOK_DISABLED] Webhooks are disabled. Skipping event [{}]", eventName);
            return false;
        }
        if (url == null || url.trim().isEmpty()) {
            log.warn("[WEBHOOK_UNCONFIGURED] Webhook URL for event [{}] is missing/blank. Skipping dispatch.", eventName);
            return false;
        }

        try {
            URI uri = new URI(url);
            if (uri.getScheme() == null || (!uri.getScheme().equalsIgnoreCase("http") && !uri.getScheme().equalsIgnoreCase("https"))) {
                log.warn("[WEBHOOK_INVALID_URL] Invalid URI scheme for event [{}] URL: {}", eventName, url);
                return false;
            }
        } catch (Exception e) {
            log.warn("[WEBHOOK_INVALID_URL] Malformed URL for event [{}]: {} ({})", eventName, url, e.getMessage());
            return false;
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
        String requestId = UUID.randomUUID().toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Event-Type", eventName);
        headers.set("X-RIVEXA-Event", eventName);
        headers.set("X-RIVEXA-Timestamp", timestamp);
        headers.set("X-RIVEXA-Request-ID", requestId);
        headers.set("X-RIVEXA-Signature", computeSignature(timestamp, requestId, payload.toString()));
        headers.set("X-Signature", computeSignature(payload.toString()));

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);
        RestTemplate client = getOrCreateRestTemplate();

        int attempts = Math.max(1, maxRetries + 1);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                log.info("[WEBHOOK_DISPATCH_ATTEMPT] Event [{}] attempt {}/{} sending to: {}", eventName, attempt, attempts, url);
                ResponseEntity<String> response = client.postForEntity(url, requestEntity, String.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("[WEBHOOK_DISPATCH_SUCCESS] Webhook [{}] delivered successfully to {} (Status: HTTP {})",
                            eventName, url, response.getStatusCode().value());
                    successCount.incrementAndGet();
                    lastSuccessfulWebhook = LocalDateTime.now();
                    return true;
                } else {
                    log.warn("[WEBHOOK_DISPATCH_WARNING] Webhook [{}] returned non-2xx status code: HTTP {} (Attempt {}/{})",
                            eventName, response.getStatusCode().value(), attempt, attempts);
                }
            } catch (Exception e) {
                log.warn("[WEBHOOK_DISPATCH_WARNING] Event [{}] attempt {}/{} failed: {}",
                        eventName, attempt, attempts, e.getMessage());
                lastError = e.getMessage();
            }

            if (attempt < attempts) {
                try {
                    Thread.sleep(150L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        failureCount.incrementAndGet();
        lastFailedWebhook = LocalDateTime.now();
        log.error("[WEBHOOK_DISPATCH_FAILED] Webhook [{}] failed after {} attempts. Core operation continues safely.",
                eventName, attempts);
        return false;
    }

    @Async
    public void triggerRegistrationVerificationWebhook(String email, String name, String verificationLink, LocalDateTime expiresAt) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "REGISTRATION_VERIFICATION");
        payload.put("email", email);
        payload.put("name", name != null ? name : "");
        payload.put("verificationLink", verificationLink);
        payload.put("expiresAt", expiresAt != null ? expiresAt.format(DateTimeFormatter.ISO_DATE_TIME) : "");
        payload.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        sendWebhook(registrationWebhookUrl, payload, "REGISTRATION_VERIFICATION");
    }

    @Async
    public void triggerLoginSuccessWebhook(String userId, String name, String email, String provider, String avatar, boolean isNewUser, String ipAddress, String userAgent) {
        String provUpper = provider != null ? provider.toUpperCase() : "EMAIL";
        String eventName = "github".equalsIgnoreCase(provider) ? "github_login" : ("google".equalsIgnoreCase(provider) ? "google_login" : "email_login");

        Map<String, Object> payload = new HashMap<>();
        payload.put("event", eventName);
        payload.put("eventType", "LOGIN_SUCCESS");
        payload.put("status", "SUCCESS");
        payload.put("userId", userId);
        payload.put("name", name != null ? name : "");
        payload.put("email", email);
        payload.put("provider", provUpper);
        payload.put("avatar", avatar != null ? avatar : "");
        payload.put("isNewUser", isNewUser);
        payload.put("ipAddress", ipAddress != null ? ipAddress : "unknown");
        payload.put("userAgent", userAgent != null ? userAgent : "unknown");

        String ua = userAgent != null ? userAgent : "";
        String os = "Unknown OS";
        if (ua.contains("Windows")) os = "Windows";
        else if (ua.contains("Macintosh") || ua.contains("Mac OS")) os = "macOS";
        else if (ua.contains("Linux")) os = "Linux";
        else if (ua.contains("Android")) os = "Android";
        else if (ua.contains("iPhone") || ua.contains("iPad")) os = "iOS";

        String browser = "Unknown Browser";
        if (ua.contains("Edg/")) browser = "Microsoft Edge";
        else if (ua.contains("Chrome/")) browser = "Google Chrome";
        else if (ua.contains("Firefox/")) browser = "Mozilla Firefox";
        else if (ua.contains("Safari/") && !ua.contains("Chrome/")) browser = "Apple Safari";

        payload.put("browser", browser);
        payload.put("operatingSystem", os);
        payload.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        sendWebhook(loginSuccessWebhookUrl, payload, "LOGIN_SUCCESS");
    }

    @Async
    public void triggerLoginFailedWebhook(String email, String ipAddress, String userAgent, int failedAttempts, int remainingAttempts) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "LOGIN_FAILED_WARNING");
        payload.put("email", email);
        payload.put("ipAddress", ipAddress != null ? ipAddress : "unknown");
        payload.put("userAgent", userAgent != null ? userAgent : "unknown");
        payload.put("failedAttempts", failedAttempts);
        payload.put("remainingAttempts", remainingAttempts);
        payload.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        sendWebhook(loginFailedWebhookUrl, payload, "LOGIN_FAILED_WARNING");
    }

    @Async
    public void triggerPasswordResetWebhook(String email, String name, String otpCode, String resetLink, LocalDateTime expiresAt, String ipAddress) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "PASSWORD_RESET_REQUEST");
        payload.put("email", email);
        payload.put("name", name != null ? name : "");
        payload.put("otpCode", otpCode);
        payload.put("resetLink", resetLink);
        payload.put("expiresAt", expiresAt != null ? expiresAt.format(DateTimeFormatter.ISO_DATE_TIME) : "");
        payload.put("ipAddress", ipAddress != null ? ipAddress : "unknown");
        payload.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        sendWebhook(passwordResetWebhookUrl, payload, "PASSWORD_RESET_REQUEST");
    }

    @Async
    public void triggerPasswordChangedWebhook(String email, String name, String ipAddress, String userAgent) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "PASSWORD_CHANGED");
        payload.put("email", email);
        payload.put("name", name != null ? name : "");
        payload.put("ipAddress", ipAddress != null ? ipAddress : "unknown");
        payload.put("userAgent", userAgent != null ? userAgent : "unknown");
        payload.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        sendWebhook(passwordChangedWebhookUrl, payload, "PASSWORD_CHANGED");
    }

    @Async
    public void triggerAccountLockedWebhook(String email, String name, String ipAddress, LocalDateTime lockedUntil) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "ACCOUNT_LOCKED");
        payload.put("email", email);
        payload.put("name", name != null ? name : "");
        payload.put("ipAddress", ipAddress != null ? ipAddress : "unknown");
        payload.put("lockedUntil", lockedUntil != null ? lockedUntil.format(DateTimeFormatter.ISO_DATE_TIME) : "");
        payload.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        sendWebhook(accountLockedWebhookUrl, payload, "ACCOUNT_LOCKED");
    }

    @Async
    public void triggerOAuthLinkedWebhook(String email, String name, String provider) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "OAUTH_ACCOUNT_LINKED");
        payload.put("email", email);
        payload.put("name", name != null ? name : "");
        payload.put("provider", provider);
        payload.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        sendWebhook(oauthLinkedWebhookUrl, payload, "OAUTH_ACCOUNT_LINKED");
    }

    @Async
    public void triggerPredictionCompletedWebhook(String predictionId, String repositoryId, String riskLevel, double failureProbability, double confidence, String triggeredBy) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "PREDICTION_COMPLETED");
        payload.put("predictionId", predictionId);
        payload.put("repositoryId", repositoryId);
        payload.put("riskLevel", riskLevel);
        payload.put("failureProbability", failureProbability);
        payload.put("confidence", confidence);
        payload.put("triggeredBy", triggeredBy != null ? triggeredBy : "SYSTEM");
        payload.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        sendWebhook(predictionCompletedWebhookUrl, payload, "PREDICTION_COMPLETED");
    }

    @Async
    public void triggerRepositorySyncWebhook(String repositoryId, String repositoryName, String gitProvider, boolean isSuccess, String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "REPOSITORY_SYNC_COMPLETED");
        payload.put("repositoryId", repositoryId);
        payload.put("repositoryName", repositoryName != null ? repositoryName : "");
        payload.put("gitProvider", gitProvider != null ? gitProvider : "GITHUB");
        payload.put("status", isSuccess ? "SUCCESS" : "FAILED");
        payload.put("message", message != null ? message : "");
        payload.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        sendWebhook(repositorySyncWebhookUrl, payload, "REPOSITORY_SYNC_COMPLETED");
    }

    @Async
    public void triggerHighRiskDetectedWebhook(String predictionId, String repositoryId, String repositoryName, String riskLevel, double riskScore, double failureProbability, String actor) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "HIGH_RISK_DETECTED");
        payload.put("predictionId", predictionId);
        payload.put("repositoryId", repositoryId);
        payload.put("repositoryName", repositoryName != null ? repositoryName : "");
        payload.put("riskLevel", riskLevel);
        payload.put("riskScore", riskScore);
        payload.put("failureProbability", failureProbability);
        payload.put("threshold", riskAlertThreshold);
        payload.put("triggeredBy", actor != null ? actor : "SYSTEM");
        payload.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        sendWebhook(highRiskDetectedWebhookUrl, payload, "HIGH_RISK_DETECTED");
    }

    @Async
    public void triggerReportGeneratedWebhook(String reportId, String repositoryId, String reportType, String format, String generatedBy) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "REPORT_GENERATED");
        payload.put("reportId", reportId);
        payload.put("repositoryId", repositoryId);
        payload.put("reportType", reportType != null ? reportType : "EXECUTIVE");
        payload.put("format", format != null ? format : "PDF");
        payload.put("generatedBy", generatedBy != null ? generatedBy : "SYSTEM");
        payload.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        sendWebhook(reportGeneratedWebhookUrl, payload, "REPORT_GENERATED");
    }

    @Async
    public void triggerLoginWebhook(String userId, String name, String email, String provider, String avatar, boolean isNewUser, String ipAddress, String userAgent) {
        triggerLoginSuccessWebhook(userId, name, email, provider, avatar, isNewUser, ipAddress, userAgent);
    }

    public Map<String, Object> getIntegrationStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", webhookEnabled);
        String baseUrl = "http://localhost:5678/webhook";
        if (repositorySyncWebhookUrl != null && repositorySyncWebhookUrl.contains("/webhook")) {
            baseUrl = repositorySyncWebhookUrl.substring(0, repositorySyncWebhookUrl.indexOf("/webhook") + 8);
        }
        status.put("baseUrl", baseUrl);
        status.put("connectTimeoutMs", connectTimeoutMs);
        status.put("readTimeoutMs", readTimeoutMs);
        status.put("maxRetries", maxRetries);
        status.put("riskAlertThreshold", riskAlertThreshold);
        status.put("successCount", successCount.get());
        status.put("failureCount", failureCount.get());
        status.put("lastSuccessfulWebhook", lastSuccessfulWebhook != null ? lastSuccessfulWebhook.format(DateTimeFormatter.ISO_DATE_TIME) : null);
        status.put("lastFailedWebhook", lastFailedWebhook != null ? lastFailedWebhook.format(DateTimeFormatter.ISO_DATE_TIME) : null);
        status.put("lastError", lastError);
        return status;
    }
}

