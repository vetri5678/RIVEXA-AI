package ai.riskvision.graveyard.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class N8nWebhookService {

    private final RestTemplate restTemplate;

    @Value("${n8n.webhook.enabled:true}")
    private boolean webhookEnabled;

    public N8nWebhookService() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1500);
        factory.setReadTimeout(2000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Value("${n8n.webhook.registration:http://localhost:5678/webhook/registration-verification}")
    private String registrationWebhookUrl;

    @Value("${n8n.webhook.login-success:http://localhost:5678/webhook/login-success}")
    private String loginSuccessWebhookUrl;

    @Value("${n8n.webhook.login-failed:http://localhost:5678/webhook/login-failed-warning}")
    private String loginFailedWebhookUrl;

    @Value("${n8n.webhook.password-reset:http://localhost:5678/webhook/password-reset}")
    private String passwordResetWebhookUrl;

    @Value("${n8n.webhook.password-changed:http://localhost:5678/webhook/password-changed}")
    private String passwordChangedWebhookUrl;

    @Value("${n8n.webhook.account-locked:http://localhost:5678/webhook/account-locked}")
    private String accountLockedWebhookUrl;

    @Value("${n8n.webhook.oauth-linked:http://localhost:5678/webhook/oauth-linked}")
    private String oauthLinkedWebhookUrl;

    @Value("${n8n.webhook.secret:rv_n8n_secret_key_2026}")
    private String webhookSecret;

    private String computeSignature(String data) {
        try {
            String secret = (webhookSecret != null && !webhookSecret.isEmpty()) ? webhookSecret : "rv_n8n_secret_key_2026";
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : rawHmac) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void sendWebhook(String url, Map<String, Object> payload, String eventName) {
        if (!webhookEnabled) {
            log.debug("n8n Webhooks are disabled. Skipping event {}.", eventName);
            return;
        }
        if (url == null || url.trim().isEmpty()) {
            log.warn("n8n Webhook URL for event {} is not configured. Skipping webhook trigger.", eventName);
            return;
        }
        try {
            log.info("Dispatching n8n webhook event [{}] to URL: {}", eventName, url);
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.set("X-Event-Type", eventName);
            headers.set("X-Signature", computeSignature(payload.toString()));

            org.springframework.http.HttpEntity<Map<String, Object>> requestEntity = new org.springframework.http.HttpEntity<>(payload, headers);
            restTemplate.postForEntity(url, requestEntity, String.class);
            log.info("Successfully dispatched n8n webhook event [{}]", eventName);
        } catch (Exception e) {
            log.error("Failed to dispatch n8n webhook event [{}]: {}", eventName, e.getMessage());
        }
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

        // Parse Browser and OS for explicit telemetry fields
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

    // Retained for backward compatibility
    @Async
    public void triggerLoginWebhook(String userId, String name, String email, String provider, String avatar, boolean isNewUser, String ipAddress, String userAgent) {
        triggerLoginSuccessWebhook(userId, name, email, provider, avatar, isNewUser, ipAddress, userAgent);
    }
}
