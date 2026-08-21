package ai.riskvision.graveyard.config;

import ai.riskvision.graveyard.entity.AuditLogEntity;
import ai.riskvision.graveyard.entity.LoginHistoryEntity;
import ai.riskvision.graveyard.entity.OAuthAccountEntity;
import ai.riskvision.graveyard.entity.UserEntity;
import ai.riskvision.graveyard.repository.AuditLogRepository;
import ai.riskvision.graveyard.repository.LoginHistoryRepository;
import ai.riskvision.graveyard.repository.OAuthAccountRepository;
import ai.riskvision.graveyard.repository.UserRepository;
import ai.riskvision.graveyard.service.LoginNotificationService;
import ai.riskvision.graveyard.service.N8nWebhookService;
import ai.riskvision.graveyard.util.UserAgentParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Optional;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Component
@Slf4j
public class CustomOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final OAuthAccountRepository oauthAccountRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final AuditLogRepository auditLogRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final N8nWebhookService n8nWebhookService;
    private final LoginNotificationService loginNotificationService;
    private final HttpCookieOAuth2AuthorizationRequestRepository httpCookieOAuth2AuthorizationRequestRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final ai.riskvision.graveyard.service.RepositorySyncService repositorySyncService;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.frontend.url:http://localhost:8080}")
    private String frontendUrl;

    public CustomOAuth2SuccessHandler(
            UserRepository userRepository,
            OAuthAccountRepository oauthAccountRepository,
            LoginHistoryRepository loginHistoryRepository,
            AuditLogRepository auditLogRepository,
            JwtTokenProvider jwtTokenProvider,
            N8nWebhookService n8nWebhookService,
            LoginNotificationService loginNotificationService,
            HttpCookieOAuth2AuthorizationRequestRepository httpCookieOAuth2AuthorizationRequestRepository,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
            @Autowired(required = false) OAuth2AuthorizedClientService authorizedClientService,
            @Autowired(required = false) ai.riskvision.graveyard.service.RepositorySyncService repositorySyncService,
            PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.oauthAccountRepository = oauthAccountRepository;
        this.loginHistoryRepository = loginHistoryRepository;
        this.auditLogRepository = auditLogRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.n8nWebhookService = n8nWebhookService;
        this.loginNotificationService = loginNotificationService;
        this.httpCookieOAuth2AuthorizationRequestRepository = httpCookieOAuth2AuthorizationRequestRepository;
        this.passwordEncoder = passwordEncoder;
        this.authorizedClientService = authorizedClientService;
        this.repositorySyncService = repositorySyncService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
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
            Long githubId = null;

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
                if (idObj instanceof Number) {
                    githubId = ((Number) idObj).longValue();
                } else if (idObj != null) {
                    try {
                        githubId = Long.parseLong(idObj.toString().trim());
                    } catch (NumberFormatException ignored) {}
                }
                email = oauth2User.getAttribute("email");
                username = oauth2User.getAttribute("login");
                fullName = oauth2User.getAttribute("name");
                if (fullName == null || fullName.trim().isEmpty()) {
                    fullName = username;
                }
                avatarUrl = oauth2User.getAttribute("avatar_url");
            }

            log.info("OAuth user: provider={}, id={}, githubId={}, email={}, username={}", provider, providerUserId, githubId, email, username);

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

            final String finalProviderUserId = providerUserId;
            final String finalFullName = fullName;
            final String finalAvatarUrl = avatarUrl;
            final String finalUsername = username;
            final Long finalGithubId = githubId;

            String extractedAccessToken = null;
            String extractedRefreshToken = null;
            LocalDateTime tokenExpiresAt = null;

            if (authorizedClientService != null) {
                try {
                    OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                            authToken.getAuthorizedClientRegistrationId(),
                            authToken.getName()
                    );
                    if (authorizedClient != null && authorizedClient.getAccessToken() != null) {
                        extractedAccessToken = authorizedClient.getAccessToken().getTokenValue();
                        if (authorizedClient.getAccessToken().getExpiresAt() != null) {
                            tokenExpiresAt = LocalDateTime.ofInstant(
                                    authorizedClient.getAccessToken().getExpiresAt(),
                                    java.time.ZoneId.systemDefault()
                            );
                        }
                        if (authorizedClient.getRefreshToken() != null) {
                            extractedRefreshToken = authorizedClient.getRefreshToken().getTokenValue();
                        }
                        log.info("[GITHUB-OAUTH] Successfully extracted OAuth access token (length={}) for registrationId={}",
                                extractedAccessToken.length(), authToken.getAuthorizedClientRegistrationId());
                    } else {
                        log.warn("[GITHUB-OAUTH] OAuth2AuthorizedClient missing access token for registrationId={}, principalName={}",
                                authToken.getAuthorizedClientRegistrationId(), authToken.getName());
                    }
                } catch (Exception tokenEx) {
                    log.warn("[GITHUB-OAUTH] Could not load OAuth2AuthorizedClient: {}", tokenEx.getMessage());
                }
            }

            // If GitHub email is private or missing, query GitHub /user/emails API using bearer token
            if ((email == null || email.trim().isEmpty() || email.contains("noreply")) && "github".equals(provider) && extractedAccessToken != null) {
                try {
                    org.springframework.web.client.RestTemplate rest = new org.springframework.web.client.RestTemplate();
                    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                    headers.setBearerAuth(extractedAccessToken);
                    headers.set("Accept", "application/vnd.github+json");
                    headers.set("User-Agent", "RiskVision-AI-Platform");
                    org.springframework.http.HttpEntity<?> entity = new org.springframework.http.HttpEntity<>(headers);
                    org.springframework.http.ResponseEntity<java.util.List<java.util.Map<String, Object>>> responseEntity = rest.exchange(
                            "https://api.github.com/user/emails",
                            org.springframework.http.HttpMethod.GET,
                            entity,
                            new org.springframework.core.ParameterizedTypeReference<java.util.List<java.util.Map<String, Object>>>() {}
                    );
                    if (responseEntity.getBody() != null) {
                        for (java.util.Map<String, Object> emailObj : responseEntity.getBody()) {
                            Boolean primary = (Boolean) emailObj.get("primary");
                            Boolean verified = (Boolean) emailObj.get("verified");
                            String emailStr = (String) emailObj.get("email");
                            if (Boolean.TRUE.equals(primary) && Boolean.TRUE.equals(verified) && emailStr != null && !emailStr.trim().isEmpty()) {
                                email = emailStr.trim();
                                log.info("[GITHUB-OAUTH] Successfully fetched primary verified GitHub email from /user/emails: {}", email);
                                break;
                            }
                        }
                    }
                } catch (Exception emailEx) {
                    log.warn("[GITHUB-OAUTH] Could not fetch private emails from GitHub API: {}", emailEx.getMessage());
                }
            }

            final String finalEmail = email != null ? email.trim().toLowerCase() : "";
            final String finalAccessToken = extractedAccessToken;
            final String finalRefreshToken = extractedRefreshToken;
            final LocalDateTime finalTokenExpiresAt = tokenExpiresAt;

            log.info("[STAGE 3: CALLBACK_RECEIVED] OAuth callback received for provider={}", provider);
            log.info("[STAGE 4: STATE_AND_CODE_VALIDATED] Code & state validated. ProviderUserId={}, email={}, username={}, githubId={}",
                    finalProviderUserId, finalEmail, finalUsername, finalGithubId);
            log.info("[STAGE 5: TOKEN_EXCHANGED] Token present={}, refreshTokenPresent={}",
                    (finalAccessToken != null && !finalAccessToken.isEmpty()), (finalRefreshToken != null && !finalRefreshToken.isEmpty()));
            log.info("[STAGE 6: GITHUB_USER_FETCHED] GitHub user attributes parsed successfully.");

            UserEntity user = transactionTemplate.execute(status -> {
                log.info("[STAGE 7: USER_AND_OAUTH_LINKED_IN_DB] Starting DB transaction for provider={}, providerUserId={}", provider, finalProviderUserId);
                UserEntity resolvedUser = null;

                // 1. Resolve Initiating User (Logged-in user connecting GitHub)
                String initRaw = ai.riskvision.graveyard.util.CookieUtils.getCookie(request, HttpCookieOAuth2AuthorizationRequestRepository.INITIATING_USER_COOKIE_NAME)
                        .map(jakarta.servlet.http.Cookie::getValue).orElse(null);
                if (initRaw == null || initRaw.trim().isEmpty()) {
                    initRaw = request.getParameter("user_email");
                }
                final String initiatingUserStr = initRaw;

                Optional<UserEntity> initiatingUserOpt = Optional.empty();
                if (initiatingUserStr != null && !initiatingUserStr.trim().isEmpty()) {
                    initiatingUserOpt = userRepository.findByEmail(initiatingUserStr.trim())
                            .or(() -> userRepository.findByUsername(initiatingUserStr.trim()));
                    if (initiatingUserOpt.isEmpty()) {
                        try {
                            UUID uuid = UUID.fromString(initiatingUserStr.trim());
                            initiatingUserOpt = userRepository.findById(uuid);
                        } catch (Exception ignored) {}
                    }
                }

                if (initiatingUserOpt.isPresent()) {
                    resolvedUser = initiatingUserOpt.get();
                    log.info("[STAGE 7: USER_AND_OAUTH_LINKED_IN_DB] Found initiating user: id={}, email={}", resolvedUser.getId(), resolvedUser.getEmail());
                    Optional<OAuthAccountEntity> existingLink = oauthAccountRepository.findByProviderAndProviderUserId(provider, finalProviderUserId);
                    if (existingLink.isPresent() && !existingLink.get().getUser().getId().equals(resolvedUser.getId())) {
                        log.info("[STAGE 7: USER_AND_OAUTH_LINKED_IN_DB] Reassigning {} account providerUserId={} from previous user {} to initiating user {}",
                                provider, finalProviderUserId, existingLink.get().getUser().getEmail(), resolvedUser.getEmail());
                        try {
                            oauthAccountRepository.delete(existingLink.get());
                            oauthAccountRepository.flush();
                        } catch (Exception delEx) {
                            log.warn("[STAGE 7: USER_AND_OAUTH_LINKED_IN_DB] Could not delete old OAuth linkage: {}", delEx.getMessage());
                        }
                    }
                } else {
                    // 2. Unauthenticated User (Sign in / Register with GitHub)
                    Optional<OAuthAccountEntity> oauthLinkOpt = oauthAccountRepository.findByProviderAndProviderUserId(provider, finalProviderUserId);
                    Optional<UserEntity> githubUserOpt = finalGithubId != null ? userRepository.findByGithubId(finalGithubId) : Optional.empty();
                    Optional<UserEntity> emailUserOpt = userRepository.findByEmail(finalEmail);

                    if (oauthLinkOpt.isPresent()) {
                        resolvedUser = oauthLinkOpt.get().getUser();
                        log.info("[STAGE 7: USER_AND_OAUTH_LINKED_IN_DB] Found user by OAuth account linkage: id={}, email={}", resolvedUser.getId(), resolvedUser.getEmail());
                    } else if (githubUserOpt.isPresent()) {
                        resolvedUser = githubUserOpt.get();
                        log.info("[STAGE 7: USER_AND_OAUTH_LINKED_IN_DB] Found user by githubId: id={}, email={}", resolvedUser.getId(), resolvedUser.getEmail());
                    } else if (emailUserOpt.isPresent()) {
                        resolvedUser = emailUserOpt.get();
                        log.info("[STAGE 7: USER_AND_OAUTH_LINKED_IN_DB] Found user by matching email: id={}, email={}", resolvedUser.getId(), resolvedUser.getEmail());
                    } else {
                        String baseUsername = finalUsername != null && !finalUsername.trim().isEmpty() ? finalUsername.trim() : finalEmail.split("@")[0];
                        String uniqueUsername = baseUsername;
                        int suffix = 1;
                        while (userRepository.existsByUsername(uniqueUsername)) {
                            uniqueUsername = baseUsername + suffix;
                            suffix++;
                        }

                        resolvedUser = UserEntity.builder()
                                .email(finalEmail)
                                .username(uniqueUsername)
                                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                                .fullName(finalFullName != null && !finalFullName.trim().isEmpty() ? finalFullName : uniqueUsername)
                                .role("viewer")
                                .isVerified(true)
                                .isActive(true)
                                .provider(provider)
                                .providerUserId(finalProviderUserId)
                                .githubId(finalGithubId)
                                .avatarUrl(finalAvatarUrl)
                                .loginCount(0)
                                .failedLoginAttempts(0)
                                .build();
                        resolvedUser = userRepository.save(resolvedUser);
                        log.info("[STAGE 7: USER_AND_OAUTH_LINKED_IN_DB] Created new user: id={}, email={}", resolvedUser.getId(), resolvedUser.getEmail());
                    }
                }

                // Reassign conflicting linkage if it exists for a different user
                Optional<OAuthAccountEntity> conflictingLinkage = oauthAccountRepository.findByProviderAndProviderUserId(provider, finalProviderUserId);
                if (conflictingLinkage.isPresent() && !conflictingLinkage.get().getUser().getId().equals(resolvedUser.getId())) {
                    log.info("[STAGE 7: USER_AND_OAUTH_LINKED_IN_DB] Reassigning linkage {} from user {} to user {}",
                            provider, conflictingLinkage.get().getUser().getEmail(), resolvedUser.getEmail());
                    try {
                        oauthAccountRepository.delete(conflictingLinkage.get());
                        oauthAccountRepository.flush();
                    } catch (Exception ignored) {}
                }

                // Safely update githubId on resolvedUser if not set and no other user owns that githubId
                if (finalGithubId != null && resolvedUser.getGithubId() == null) {
                    Optional<UserEntity> ownerOpt = userRepository.findByGithubId(finalGithubId);
                    if (ownerOpt.isEmpty() || ownerOpt.get().getId().equals(resolvedUser.getId())) {
                        resolvedUser.setGithubId(finalGithubId);
                    }
                }

                // Save or update OAuthAccountEntity linkage with token
                final UserEntity userToLink = resolvedUser;
                Optional<OAuthAccountEntity> existingLinkage = oauthAccountRepository.findByUserAndProvider(userToLink, provider);
                OAuthAccountEntity linkage = existingLinkage.orElseGet(() -> OAuthAccountEntity.builder()
                        .user(userToLink)
                        .provider(provider)
                        .providerUserId(finalProviderUserId)
                        .build());

                linkage.setProviderUserId(finalProviderUserId);
                if (finalAccessToken != null && !finalAccessToken.trim().isEmpty()) {
                    linkage.setAccessToken(finalAccessToken.trim());
                }
                if (finalRefreshToken != null && !finalRefreshToken.trim().isEmpty()) {
                    linkage.setRefreshToken(finalRefreshToken.trim());
                }
                if (finalTokenExpiresAt != null) {
                    linkage.setExpiresAt(finalTokenExpiresAt);
                }

                oauthAccountRepository.save(linkage);

                if (resolvedUser.getRole() == null || resolvedUser.getRole().trim().isEmpty()) {
                    resolvedUser.setRole("viewer");
                }
                resolvedUser.setLoginCount((resolvedUser.getLoginCount() != null ? resolvedUser.getLoginCount() : 0) + 1);
                resolvedUser.setLastLogin(LocalDateTime.now());
                // Only update provider if it's currently unset or 'email' (don't overwrite a different OAuth provider)
                if (resolvedUser.getProvider() == null || resolvedUser.getProvider().trim().isEmpty()
                        || resolvedUser.getProvider().equals("email")) {
                    resolvedUser.setProvider(provider);
                }
                if (finalAvatarUrl != null && !finalAvatarUrl.trim().isEmpty()) {
                    resolvedUser.setAvatarUrl(finalAvatarUrl);
                }
                if (finalFullName != null && !finalFullName.trim().isEmpty()
                        && (resolvedUser.getFullName() == null || resolvedUser.getFullName().isEmpty())) {
                    resolvedUser.setFullName(finalFullName);
                }
                UserEntity savedUser = userRepository.save(resolvedUser);
                log.info("[STAGE 7: USER_AND_OAUTH_LINKED_IN_DB] DB linkage completed cleanly for user id={}, email={}", savedUser.getId(), savedUser.getEmail());
                return savedUser;
            });

            if ("github".equals(provider) && user != null && finalAccessToken != null && repositorySyncService != null) {
                try {
                    log.info("[GITHUB-OAUTH] Triggering background repository sync for user email={}", user.getEmail());
                    repositorySyncService.syncUserGitHubRepositories(user, finalAccessToken);
                } catch (Exception syncEx) {
                    log.warn("[GITHUB-OAUTH] Auto repo sync after login encountered error: {}", syncEx.getMessage());
                }
            }

            // Remove the authorized client from Spring's in-memory store after we've
            // persisted the token to our own DB. This prevents stale token reuse across sessions
            // and ensures the next login always goes through a fresh GitHub authorization round-trip.
            if (authorizedClientService != null) {
                try {
                    authorizedClientService.removeAuthorizedClient(
                            authToken.getAuthorizedClientRegistrationId(),
                            authToken.getName());
                    log.info("[GITHUB-OAUTH] Removed authorized client from in-memory store for principalName={}",
                            authToken.getName());
                } catch (Exception ex) {
                    log.debug("[GITHUB-OAUTH] Could not remove authorized client: {}", ex.getMessage());
                }
            }

            if (user == null) {
                httpCookieOAuth2AuthorizationRequestRepository.removeAuthorizationRequestCookies(request, response);
                String errorMsg = java.net.URLEncoder.encode("This GitHub account is already linked to another RIVEXA account.",
                        java.nio.charset.StandardCharsets.UTF_8);
                response.sendRedirect(baseUrl + "/#/login?error=" + errorMsg);
                return;
            }

            boolean isNewUser = user.getLoginCount() == null || user.getLoginCount() <= 1;

            // ── Resolve IP and User-Agent ────────────────────────────────────────
            String ipAddress = request.getHeader("X-Forwarded-For");
            if (ipAddress == null || ipAddress.isEmpty()) {
                ipAddress = request.getRemoteAddr();
            }
            String userAgent = request.getHeader("User-Agent");
            String browser = UserAgentParser.parseBrowser(userAgent);
            String operatingSystem = UserAgentParser.parseOS(userAgent);

            // ── Create JWT tokens ───────────────────────────────────────────────
            log.info("[STAGE 8: RIVEXA_SESSION_AND_JWT_CREATED] Generating JWT access & refresh tokens for user id={}, email={}", user.getId(), user.getEmail());
            String accessToken = jwtTokenProvider.generateAccessToken(
                    user.getEmail(),
                    user.getRole(),
                    user.getId().toString(),
                    provider,
                    user.getUsername());
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail());

            // ── Compute session correlation ID ──────────────────────────────────
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
            LoginHistoryEntity history = null;
            try {
                history = loginHistoryRepository.save(LoginHistoryEntity.builder()
                        .user(user)
                        .email(user.getEmail())
                        .ipAddress(ipAddress)
                        .userAgent(userAgent)
                        .provider(provider)
                        .browser(browser)
                        .operatingSystem(operatingSystem)
                        .sessionId(sessionCorrelationId)
                        .success(true)
                        .emailNotified(false)
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
                log.warn("Failed to persist login history/audit log for OAuth user {}: {}", user.getEmail(), ex.getMessage());
            }

            // ── n8n Webhook trigger ─────────────────────────────────────────
            try {
                n8nWebhookService.triggerLoginWebhook(
                        user.getId().toString(),
                        user.getFullName(),
                        user.getEmail(),
                        provider,
                        user.getAvatarUrl(),
                        isNewUser,
                        ipAddress,
                        userAgent);
            } catch (Exception ex) {
                log.warn("n8n webhook notification failed: {}", ex.getMessage());
            }

            // ── Idempotent Login Notification Email ─────────────────────────────
            if (history != null && history.getId() != null) {
                try {
                    loginNotificationService.sendAdminLoginNotification(history.getId());
                } catch (Exception ex) {
                    log.warn("Admin login notification failed: {}", ex.getMessage());
                }
            }

            String targetUrl = baseUrl + "/#/auth/callback" +
                    "?token=" + java.net.URLEncoder.encode(accessToken, java.nio.charset.StandardCharsets.UTF_8) +
                    "&refreshToken=" + java.net.URLEncoder.encode(refreshToken, java.nio.charset.StandardCharsets.UTF_8) +
                    "&username=" + java.net.URLEncoder.encode(user.getUsername() != null ? user.getUsername() : "", java.nio.charset.StandardCharsets.UTF_8);

            log.info("[STAGE 9: REDIRECT_TO_FRONTEND_CALLBACK] Redirecting authenticated user to frontend callback target: {}", targetUrl);
            httpCookieOAuth2AuthorizationRequestRepository.removeAuthorizationRequestCookies(request, response);
            if (!response.isCommitted()) {
                response.sendRedirect(targetUrl);
            }
        } catch (Exception ex) {
            log.error("[STAGE 7: DATABASE_LINKING_FAILED] Unhandled error in OAuth2 success handler: ", ex);
            httpCookieOAuth2AuthorizationRequestRepository.removeAuthorizationRequestCookies(request, response);
            if (!response.isCommitted()) {
                String userMsg = ex.getMessage();
                if (userMsg == null || userMsg.trim().isEmpty()) {
                    userMsg = ("google".equalsIgnoreCase(provider) ? "Google" : "GitHub") + " authentication failed. Please try again.";
                }
                String errorMsg = java.net.URLEncoder.encode(userMsg, java.nio.charset.StandardCharsets.UTF_8);
                response.sendRedirect(baseUrl + "/#/login?error=" + errorMsg);
            }
        }
    }
}
