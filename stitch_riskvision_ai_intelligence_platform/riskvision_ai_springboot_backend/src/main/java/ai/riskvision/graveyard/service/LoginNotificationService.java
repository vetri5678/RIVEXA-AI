package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.entity.LoginHistoryEntity;
import ai.riskvision.graveyard.entity.UserEntity;
import ai.riskvision.graveyard.repository.LoginHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Backend-authoritative service that enforces strict authentication success verification
 * and database idempotency before dispatching admin security notification emails.
 *
 * <p>Requirements:
 * <ul>
 *   <li>Notification MUST be tied to a verified successful {@link LoginHistoryEntity} event.</li>
 *   <li>Each login event generates at most ONE admin notification (Idempotency).</li>
 *   <li>Email delivery failure does NOT fail the authentication flow or throw exceptions.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginNotificationService {

    private final LoginHistoryRepository loginHistoryRepository;
    private final EmailService emailService;

    /**
     * Dispatches an admin security notification for a verified, successful login event.
     * Enforces database idempotency using {@code LoginHistoryEntity.emailNotified}.
     *
     * @param loginHistoryId UUID of the persisted login history record
     */
    @Transactional
    public void sendAdminLoginNotification(UUID loginHistoryId) {
        if (loginHistoryId == null) {
            log.warn("🚨 [SECURITY_GUARD] Rejecting login notification request: NULL loginHistoryId provided.");
            return;
        }

        LoginHistoryEntity history = loginHistoryRepository.findById(loginHistoryId).orElse(null);
        if (history == null) {
            log.warn("🚨 [SECURITY_GUARD] Rejecting login notification: Login history record {} not found.", loginHistoryId);
            return;
        }

        // 1. Strict Authentication Success Guard
        if (!Boolean.TRUE.equals(history.getSuccess())) {
            log.warn("🚨 [SECURITY_GUARD] Rejecting login notification for event {}: Authentication event was not successful.", loginHistoryId);
            return;
        }

        // 2. Database Idempotency Guard
        if (Boolean.TRUE.equals(history.getEmailNotified())) {
            log.info("ℹ️ [IDEMPOTENCY_GUARD] Skipping duplicate email dispatch: Login event {} has already been notified.", loginHistoryId);
            return;
        }

        // 3. Mark as notified BEFORE dispatching email to prevent race condition double-sending
        history.setEmailNotified(true);
        loginHistoryRepository.save(history);

        UserEntity user = history.getUser();
        String userId = user != null ? user.getId().toString() : "N/A";
        String userEmail = history.getEmail() != null ? history.getEmail() : (user != null ? user.getEmail() : "unknown@riskvision.ai");
        String displayName = (user != null && user.getFullName() != null && !user.getFullName().isBlank())
                ? user.getFullName()
                : (user != null ? user.getUsername() : userEmail);

        String rawProvider = history.getProvider() != null ? history.getProvider() : "email";
        String providerLabel = "google".equalsIgnoreCase(rawProvider) ? "Google OAuth"
                : "github".equalsIgnoreCase(rawProvider) ? "GitHub OAuth"
                : "Credentials".equalsIgnoreCase(rawProvider) || "email".equalsIgnoreCase(rawProvider) ? "Credentials"
                : rawProvider.substring(0, 1).toUpperCase() + rawProvider.substring(1) + " OAuth";

        String browser = history.getBrowser() != null ? history.getBrowser() : "Unknown Browser";
        String os = history.getOperatingSystem() != null ? history.getOperatingSystem() : "Unknown OS";
        String ip = history.getIpAddress() != null ? history.getIpAddress() : "127.0.0.1";
        LocalDateTime loginTime = history.getCreatedAt() != null ? history.getCreatedAt() : LocalDateTime.now();

        log.info("📧 [LOGIN_NOTIFICATION_QUEUED] EventID={}, User={}, Provider={}, IP={}",
                loginHistoryId, userEmail, providerLabel, ip);

        // 4. Safe Async Email Dispatch (Failure won't break auth)
        try {
            emailService.sendLoginNotificationEmail(
                    userId,
                    userEmail,
                    displayName,
                    loginTime,
                    providerLabel,
                    browser,
                    os,
                    ip);
            log.info("✅ [LOGIN_NOTIFICATION_SENT] Successfully queued async admin email for login event {}", loginHistoryId);
        } catch (Exception ex) {
            log.error("❌ [LOGIN_NOTIFICATION_FAILED] Failed to queue login email for event {}: {}", loginHistoryId, ex.getMessage());
        }
    }
}
