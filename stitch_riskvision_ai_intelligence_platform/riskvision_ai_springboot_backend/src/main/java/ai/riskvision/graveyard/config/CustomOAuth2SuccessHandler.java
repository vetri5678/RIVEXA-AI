package ai.riskvision.graveyard.config;

import ai.riskvision.graveyard.entity.AuditLogEntity;
import ai.riskvision.graveyard.entity.LoginHistoryEntity;
import ai.riskvision.graveyard.entity.OAuthAccountEntity;
import ai.riskvision.graveyard.entity.UserEntity;
import ai.riskvision.graveyard.repository.AuditLogRepository;
import ai.riskvision.graveyard.repository.LoginHistoryRepository;
import ai.riskvision.graveyard.repository.OAuthAccountRepository;
import ai.riskvision.graveyard.repository.UserRepository;
import ai.riskvision.graveyard.service.EmailService;
import ai.riskvision.graveyard.service.N8nWebhookService;
import ai.riskvision.graveyard.util.UserAgentParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@Slf4j
public class CustomOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final OAuthAccountRepository oauthAccountRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final AuditLogRepository auditLogRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final N8nWebhookService n8nWebhookService;
    private final EmailService emailService;
    private final HttpCookieOAuth2AuthorizationRequestRepository httpCookieOAuth2AuthorizationRequestRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Value("${app.frontend.url:http://localhost:8080}")
    private String frontendUrl;

    public CustomOAuth2SuccessHandler(
            UserRepository userRepository,
            OAuthAccountRepository oauthAccountRepository,
            LoginHistoryRepository loginHistoryRepository,
            AuditLogRepository auditLogRepository,
            JwtTokenProvider jwtTokenProvider,
            N8nWebhookService n8nWebhookService,
            EmailService emailService,
            HttpCookieOAuth2AuthorizationRequestRepository httpCookieOAuth2AuthorizationRequestRepository,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.oauthAccountRepository = oauthAccountRepository;
        this.loginHistoryRepository = loginHistoryRepository;
        this.auditLogRepository = auditLogRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.n8nWebhookService = n8nWebhookService;
        this.emailService = emailService;
        this.httpCookieOAuth2AuthorizationRequestRepository = httpCookieOAuth2AuthorizationRequestRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication)
            throws IOException, ServletException {

        OAuth2AuthenticationToken authToken = (OAuth2AuthenticationToken) authentication;
        String provider = authToken.getAuthorizedClientRegistrationId().toLowerCase();
        OAuth2User oauth2User = authToken.getPrincipal();

        log.info("OAuth2 success handler triggered for provider: {}", provider);

        String baseUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        if (baseUrl.endsWith("/dashboard")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - "/dashboard".length());
        }

        try {
            String providerUserId = "";
            String email = "";
            String fullName = "";
            String avatarUrl = "";
            String username = "";

            if ("google".equals(provider)) {
                providerUserId = oauth2User.getAttribute("sub");
                email = oauth2User.getAttribute("email");
                fullName = oauth2User.getAttribute("name");
                avatarUrl = oauth2User.getAttribute("picture");
                if (email != null && !email.isEmpty()) {
                    username = email.split("@")[0];
                }
            } else if ("github".equals(provider)) {
                Object idObj = oauth2User.getAttribute("id");
                providerUserId = idObj != null ? idObj.toString() : "";
                email = oauth2User.getAttribute("email");
                username = oauth2User.getAttribute("login");
                fullName = oauth2User.getAttribute("name");
                if (fullName == null || fullName.trim().isEmpty()) {
                    fullName = username;
                }
                avatarUrl = oauth2User.getAttribute("avatar_url");
            }

            log.info("OAuth user: id={}, email={}, username={}", providerUserId, email, username);

            // If GitHub email is private/null, auto-generate GitHub noreply email alias
            if ((email == null || email.trim().isEmpty()) && "github".equals(provider) && username != null
                    && !username.trim().isEmpty()) {
                email = username.trim().toLowerCase() + "@users.noreply.github.com";
                log.info("GitHub user has private email. Generated noreply alias: {}", email);
            }

            // Fallback for any remaining missing email
            if (email == null || email.trim().isEmpty()) {
                log.warn("OAuth provider {} did not return email. Redirecting to email collector page on frontend.",
                        provider);
                String redirectUrl = baseUrl + "/#/oauth2/email-required" +
                        "?provider=" + java.net.URLEncoder.encode(provider, java.nio.charset.StandardCharsets.UTF_8) +
                        "&providerUserId="
                        + java.net.URLEncoder.encode(providerUserId != null ? providerUserId : "",
                                java.nio.charset.StandardCharsets.UTF_8)
                        +
                        "&avatarUrl="
                        + java.net.URLEncoder.encode(avatarUrl != null ? avatarUrl : "",
                                java.nio.charset.StandardCharsets.UTF_8)
                        +
                        "&username="
                        + java.net.URLEncoder.encode(username != null ? username : "",
                                java.nio.charset.StandardCharsets.UTF_8)
                        +
                        "&fullName=" + java.net.URLEncoder.encode(fullName != null ? fullName : "",
                                java.nio.charset.StandardCharsets.UTF_8);
                response.sendRedirect(redirectUrl);
                return;
            }

            email = email.trim().toLowerCase();
            boolean isNewUser = false;
            UserEntity user;

            // Check if there is an OAuth account link
            var oauthLink = oauthAccountRepository.findByProviderAndProviderUserId(provider, providerUserId);
            if (oauthLink.isPresent()) {
                user = oauthLink.get().getUser();
                log.info("OAuth account already linked to user: {}", user.getEmail());
            } else {
                // Check if user already exists with this email
                var existingUserOpt = userRepository.findByEmail(email);
                if (existingUserOpt.isPresent()) {
                    user = existingUserOpt.get();
                    log.info("User with email {} already exists. Linking OAuth account.", email);
                } else {
                    isNewUser = true;
                    // Generate a unique username if taken
                    String baseUsername = username != null && !username.trim().isEmpty() ? username.trim()
                            : email.split("@")[0];
                    String uniqueUsername = baseUsername;
                    int suffix = 1;
                    while (userRepository.existsByUsername(uniqueUsername)) {
                        uniqueUsername = baseUsername + suffix;
                        suffix++;
                    }

                    user = UserEntity.builder()
                            .email(email)
                            .username(uniqueUsername)
                            .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                            .fullName(fullName != null && !fullName.trim().isEmpty() ? fullName : uniqueUsername)
                            .role("viewer")
                            .isVerified(true)
                            .isActive(true)
                            .provider(provider)
                            .providerUserId(providerUserId)
                            .avatarUrl(avatarUrl)
                            .loginCount(0)
                            .failedLoginAttempts(0)
                            .build();
                    user = userRepository.save(user);
                    log.info("Automatically created new user account: {}", email);

                    try {
                        emailService.sendWelcomeEmail(email, user.getUsername());
                    } catch (Exception e) {
                        log.error("Welcome email failed: {}", e.getMessage());
                    }
                }

                // Create OAuth account linkage
                OAuthAccountEntity linkage = OAuthAccountEntity.builder()
                        .user(user)
                        .provider(provider)
                        .providerUserId(providerUserId)
                        .build();
                oauthAccountRepository.save(linkage);
            }

            // Sync statistics and details
            if (user.getRole() == null || user.getRole().trim().isEmpty()) {
                user.setRole("viewer");
            }
            user.setLoginCount((user.getLoginCount() != null ? user.getLoginCount() : 0) + 1);
            user.setLastLogin(LocalDateTime.now());
            user.setProvider(provider);
            if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
                user.setAvatarUrl(avatarUrl);
            }
            if (fullName != null && !fullName.trim().isEmpty()
                    && (user.getFullName() == null || user.getFullName().isEmpty())) {
                user.setFullName(fullName);
            }
            userRepository.save(user);

            // ── Resolve IP and User-Agent ────────────────────────────────────────
            String ipAddress = request.getHeader("X-Forwarded-For");
            if (ipAddress == null || ipAddress.isEmpty()) {
                ipAddress = request.getRemoteAddr();
            }
            String userAgent = request.getHeader("User-Agent");
            String browser = UserAgentParser.parseBrowser(userAgent);
            String operatingSystem = UserAgentParser.parseOS(userAgent);

            // ── Create JWT tokens (must be first — session hash depends on accessToken) ─
            String accessToken = jwtTokenProvider.generateAccessToken(
                    user.getEmail(),
                    user.getRole(),
                    user.getId().toString(),
                    provider,
                    user.getUsername());
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail());

            // ── Compute session correlation ID (first 16 hex chars of SHA-256(accessToken)) ─
            String sessionCorrelationId = null;
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                byte[] hashBytes = md.digest(accessToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 8; i++) sb.append(String.format("%02x", hashBytes[i]));
                sessionCorrelationId = sb.toString();
            } catch (Exception ex) {
                log.debug("Could not compute session correlation ID: {}", ex.getMessage());
            }

            // ── Persist login history and audit log ──────────────────────────
            try {
                loginHistoryRepository.save(LoginHistoryEntity.builder()
                        .user(user)
                        .email(user.getEmail())
                        .ipAddress(ipAddress)
                        .userAgent(userAgent)
                        .provider(provider)
                        .browser(browser)
                        .operatingSystem(operatingSystem)
                        .sessionId(sessionCorrelationId)
                        .success(true)
                        .build());

                auditLogRepository.save(AuditLogEntity.builder()
                        .user(user)
                        .eventType("OAUTH_LOGIN_SUCCESS")
                        .eventTypeCompat("OAUTH_LOGIN_SUCCESS")
                        .details("User successfully authenticated via " + provider.toUpperCase() + " OAuth2"
                                + " | Browser: " + browser
                                + " | OS: " + operatingSystem
                                + " | IP: " + ipAddress)
                        .ipAddress(ipAddress)
                        .build());
            } catch (Exception ex) {
                log.warn("Failed to persist login history/audit log for OAuth user {}: {}", user.getEmail(),
                        ex.getMessage());
            }

            // ── n8n Webhook trigger ─────────────────────────────────────────
            n8nWebhookService.triggerLoginWebhook(
                    user.getId().toString(),
                    user.getFullName(),
                    user.getEmail(),
                    provider,
                    user.getAvatarUrl(),
                    isNewUser,
                    ipAddress,
                    userAgent);

            // ── Login notification email (async — never blocks redirect) ──────────
            // JWT is NOT embedded in the email URL. The "Go to Dashboard" link
            // points to /#/dashboard; the frontend PrivateRoute handles session validation.
            try {
                String providerLabel = "google".equals(provider) ? "Google OAuth"
                        : "github".equals(provider) ? "GitHub OAuth"
                        : provider.substring(0, 1).toUpperCase() + provider.substring(1) + " OAuth";
                emailService.sendLoginNotificationEmail(
                        user.getEmail(),
                        user.getFullName() != null && !user.getFullName().isBlank()
                                ? user.getFullName() : user.getUsername(),
                        LocalDateTime.now(),
                        providerLabel,
                        browser,
                        operatingSystem,
                        ipAddress);
                log.info("Login notification email dispatched async for user: {}", user.getEmail());
            } catch (Exception ex) {
                log.error("Login notification email dispatch failed for {}: {}", user.getEmail(), ex.getMessage());
            }

            String targetUrl = baseUrl + "/#/oauth2/callback" +
                    "?token=" + java.net.URLEncoder.encode(accessToken, java.nio.charset.StandardCharsets.UTF_8) +
                    "&refreshToken=" + java.net.URLEncoder.encode(refreshToken, java.nio.charset.StandardCharsets.UTF_8)
                    +
                    "&username=" + java.net.URLEncoder.encode(user.getUsername() != null ? user.getUsername() : "",
                            java.nio.charset.StandardCharsets.UTF_8);

            log.info("Redirecting OAuth user to: {}", targetUrl);
            httpCookieOAuth2AuthorizationRequestRepository.removeAuthorizationRequestCookies(request, response);
            response.sendRedirect(targetUrl);
        } catch (Exception ex) {
            log.error("Unhandled error in OAuth2 success handler: ", ex);
            httpCookieOAuth2AuthorizationRequestRepository.removeAuthorizationRequestCookies(request, response);
            String errorMsg = java.net.URLEncoder.encode("OAuth login processing failed: " + ex.getMessage(),
                    java.nio.charset.StandardCharsets.UTF_8);
            response.sendRedirect(baseUrl + "/#/login?error=" + errorMsg);
        }
    }
}
