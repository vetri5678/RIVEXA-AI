package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.config.JwtTokenProvider;
import ai.riskvision.graveyard.dto.auth.TokenResponse;
import ai.riskvision.graveyard.dto.auth.UserLoginRequest;
import ai.riskvision.graveyard.dto.auth.UserRegisterRequest;
import ai.riskvision.graveyard.dto.auth.UserResponse;
import ai.riskvision.graveyard.entity.*;
import ai.riskvision.graveyard.model.UserRole;
import ai.riskvision.graveyard.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final OAuthAccountRepository oauthAccountRepository;
    private final N8nWebhookService n8nWebhookService;
    private final VerificationTokenRepository verificationTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final AuditLogRepository auditLogRepository;

    @Value("${app.frontend.url:http://localhost:8080}")
    private String frontendUrl;

    @Value("${app.auth.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${app.auth.lock-duration-minutes:15}")
    private int lockDurationMinutes;

    private String getClientIp() {
        try {
            jakarta.servlet.http.HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder
                    .currentRequestAttributes()).getRequest();
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty()) {
                ip = request.getRemoteAddr();
            }
            return ip;
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String getUserAgent() {
        try {
            jakarta.servlet.http.HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder
                    .currentRequestAttributes()).getRequest();
            return request.getHeader("User-Agent");
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private void recordAudit(UserEntity user, String eventType, String details) {
        try {
            String severity = "LOW";
            if (eventType.contains("FAILED") || eventType.contains("UNAUTHORIZED")) {
                severity = "HIGH";
            } else if (eventType.contains("LOGIN") || eventType.contains("LOGOUT")) {
                severity = "MEDIUM";
            } else if (eventType.contains("REGISTER")) {
                severity = "MEDIUM";
            }

            AuditLogEntity logEntity = AuditLogEntity.builder()
                    .user(user)
                    .eventType(eventType)
                    .eventTypeCompat(eventType)
                    .module("AUTH")
                    .severity(severity)
                    .details(details)
                    .username(user != null ? user.getEmail() : null)
                    .ipAddress(getClientIp())
                    .build();
            auditLogRepository.save(logEntity);
        } catch (Exception e) {
            log.error("Failed to record audit log [{}]: {}", eventType, e.getMessage());
        }
    }

    private void recordLoginHistory(UserEntity user, String email, boolean success, String failureReason) {
        try {
            LoginHistoryEntity history = LoginHistoryEntity.builder()
                    .user(user)
                    .email(email)
                    .ipAddress(getClientIp())
                    .userAgent(getUserAgent())
                    .success(success)
                    .failureReason(failureReason)
                    .build();
            loginHistoryRepository.save(history);
        } catch (Exception e) {
            log.error("Failed to record login history: {}", e.getMessage());
        }
    }

    @Transactional
    public UserResponse register(UserRegisterRequest request) {
        String emailNorm = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(emailNorm)) {
            throw new IllegalArgumentException("Email already registered.");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already taken.");
        }

        UserEntity user = UserEntity.builder()
                .email(emailNorm)
                .username(request.getUsername().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(UserRole.VIEWER.name().toLowerCase())
                .isVerified(false)
                .isActive(false)
                .provider("email")
                .loginCount(0)
                .failedLoginAttempts(0)
                .build();

        user = userRepository.save(user);

        // Generate Verification Token (24 hours)
        String tokenStr = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);
        VerificationTokenEntity verifyToken = VerificationTokenEntity.builder()
                .user(user)
                .token(tokenStr)
                .tokenType("EMAIL_VERIFICATION")
                .expiresAt(expiresAt)
                .used(false)
                .build();
        verificationTokenRepository.save(verifyToken);

        String verifyLink = frontendUrl + "/#/verify-email?token=" + tokenStr;

        // Trigger n8n async webhook
        n8nWebhookService.triggerRegistrationVerificationWebhook(
                user.getEmail(),
                user.getFullName(),
                verifyLink,
                expiresAt);

        // Direct SMTP fallback
        try {
            emailService.sendVerificationEmail(user.getEmail(), tokenStr);
        } catch (Exception e) {
            log.error("Fallback verification email failed: {}", e.getMessage());
        }

        recordAudit(user, "USER_REGISTERED", "Registration initiated. Verification token issued.");
        return mapToUserResponse(user);
    }

    @Transactional
    public void verifyEmail(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Verification token is required.");
        }

        VerificationTokenEntity vToken = verificationTokenRepository
                .findByTokenAndTokenType(token, "EMAIL_VERIFICATION")
                .orElseThrow(() -> new IllegalArgumentException("Invalid or non-existent verification token."));

        if (Boolean.TRUE.equals(vToken.getUsed())) {
            throw new IllegalArgumentException("This verification token has already been used. Please log in.");
        }

        if (vToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException(
                    "Verification token has expired. Please request a new verification email.");
        }

        UserEntity user = vToken.getUser();
        user.setIsVerified(true);
        user.setIsActive(true);
        userRepository.save(user);

        vToken.setUsed(true);
        verificationTokenRepository.save(vToken);

        recordAudit(user, "EMAIL_VERIFIED", "Email address verified successfully via token.");
    }

    @Transactional
    public void resendVerification(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email address is required.");
        }

        UserEntity user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("No account found with this email."));

        if (Boolean.TRUE.equals(user.getIsVerified())) {
            throw new IllegalArgumentException("This account is already verified. Please sign in.");
        }

        // Invalidate old tokens
        var oldTokens = verificationTokenRepository.findByUserAndTokenTypeAndUsedFalse(user, "EMAIL_VERIFICATION");
        for (VerificationTokenEntity t : oldTokens) {
            t.setUsed(true);
            verificationTokenRepository.save(t);
        }

        // Generate new Token
        String tokenStr = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);
        VerificationTokenEntity newToken = VerificationTokenEntity.builder()
                .user(user)
                .token(tokenStr)
                .tokenType("EMAIL_VERIFICATION")
                .expiresAt(expiresAt)
                .used(false)
                .build();
        verificationTokenRepository.save(newToken);

        String verifyLink = frontendUrl + "/#/verify-email?token=" + tokenStr;

        n8nWebhookService.triggerRegistrationVerificationWebhook(
                user.getEmail(),
                user.getFullName(),
                verifyLink,
                expiresAt);

        try {
            emailService.sendVerificationEmail(user.getEmail(), tokenStr);
        } catch (Exception e) {
            log.error("Fallback verification email failed: {}", e.getMessage());
        }

        recordAudit(user, "VERIFICATION_RESENT", "New verification link issued.");
    }

    @Transactional
    public TokenResponse login(UserLoginRequest request) {
        String identifier = request.getEmail().trim().toLowerCase();
        UserEntity user = userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByUsername(identifier))
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password."));

        // Account Lock Check
        if (user.getLockedUntil() != null) {
            if (user.getLockedUntil().isAfter(LocalDateTime.now())) {
                recordLoginHistory(user, identifier, false, "ACCOUNT_LOCKED");
                throw new IllegalArgumentException(
                        "Account is temporarily locked due to multiple failed login attempts. Try again after "
                                + user.getLockedUntil());
            } else {
                // Lock period expired
                user.setLockedUntil(null);
                user.setFailedLoginAttempts(0);
                userRepository.save(user);
            }
        }

        // Email Verification Check
        if (!Boolean.TRUE.equals(user.getIsVerified())) {
            recordLoginHistory(user, identifier, false, "EMAIL_NOT_VERIFIED");
            throw new IllegalArgumentException(
                    "Email address not verified. Please check your inbox or click 'Resend Verification'.");
        }

        // Password Verification
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            int newAttempts = (user.getFailedLoginAttempts() != null ? user.getFailedLoginAttempts() : 0) + 1;
            user.setFailedLoginAttempts(newAttempts);

            if (newAttempts >= maxFailedAttempts) {
                LocalDateTime lockTime = LocalDateTime.now().plusMinutes(lockDurationMinutes);
                user.setLockedUntil(lockTime);
                userRepository.save(user);

                recordLoginHistory(user, identifier, false, "ACCOUNT_LOCKED_5_FAILURES");
                recordAudit(user, "ACCOUNT_LOCKED", maxFailedAttempts + " consecutive failed login attempts.");

                n8nWebhookService.triggerAccountLockedWebhook(
                        user.getEmail(),
                        user.getFullName(),
                        getClientIp(),
                        lockTime);

                throw new IllegalArgumentException(
                        "Account locked due to " + maxFailedAttempts
                                + " consecutive failed login attempts. Try again in " + lockDurationMinutes
                                + " minutes.");
            } else {
                userRepository.save(user);
                int remaining = maxFailedAttempts - newAttempts;
                recordLoginHistory(user, identifier, false, "WRONG_PASSWORD");

                n8nWebhookService.triggerLoginFailedWebhook(
                        user.getEmail(),
                        getClientIp(),
                        getUserAgent(),
                        newAttempts,
                        remaining);

                throw new IllegalArgumentException(
                        "Invalid email or password. Remaining attempts before lockout: " + remaining);
            }
        }

        // Success Reset Lock Counters
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLoginCount((user.getLoginCount() != null ? user.getLoginCount() : 0) + 1);
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        recordLoginHistory(user, identifier, true, null);
        recordAudit(user, "LOGIN_SUCCESS", "User logged in with email/password.");

        n8nWebhookService.triggerLoginSuccessWebhook(
                user.getId().toString(),
                user.getFullName(),
                user.getEmail(),
                user.getProvider() != null ? user.getProvider() : "email",
                user.getAvatarUrl(),
                false,
                getClientIp(),
                getUserAgent());

        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getEmail(),
                user.getRole(),
                user.getId().toString(),
                user.getProvider() != null ? user.getProvider() : "email",
                user.getUsername());
        String rawRefreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail());

        // Persist Refresh Token
        RefreshTokenEntity refreshTokenEntity = RefreshTokenEntity.builder()
                .user(user)
                .tokenHash(hashToken(rawRefreshToken))
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshTokenEntity);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .build();
    }

    @Transactional
    public TokenResponse refreshToken(String refreshToken) {
        // Strictly validate as refresh token — access tokens cannot be used here
        if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid or expired refresh token.");
        }

        String hash = hashToken(refreshToken);
        RefreshTokenEntity storedToken = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(
                        () -> new IllegalArgumentException("Refresh token is not recognized or has been revoked."));

        if (Boolean.TRUE.equals(storedToken.getRevoked()) || storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Refresh token has expired or been revoked. Please sign in again.");
        }

        // Revoke old token
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        String username = jwtTokenProvider.getUsername(refreshToken);
        UserEntity user = userRepository.findByEmail(username)
                .or(() -> userRepository.findByUsername(username))
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        String newAccessToken = jwtTokenProvider.generateAccessToken(
                user.getEmail(),
                user.getRole(),
                user.getId().toString(),
                user.getProvider() != null ? user.getProvider() : "email",
                user.getUsername());
        String newRawRefreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail());

        // Store new refresh token
        RefreshTokenEntity newRefreshTokenEntity = RefreshTokenEntity.builder()
                .user(user)
                .tokenHash(hashToken(newRawRefreshToken))
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();
        refreshTokenRepository.save(newRefreshTokenEntity);

        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRawRefreshToken)
                .build();
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.trim().isEmpty()) {
            String hash = hashToken(refreshToken);
            refreshTokenRepository.findByTokenHash(hash).ifPresent(t -> {
                t.setRevoked(true);
                refreshTokenRepository.save(t);
                recordAudit(t.getUser(), "LOGOUT", "User logged out. Refresh token revoked.");
            });
        }
    }

    public UserResponse getMe(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
        return mapToUserResponse(user);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        String identifier = email != null ? email.trim().toLowerCase() : "";
        UserEntity user = userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByUsername(identifier))
                .orElse(null);

        if (user != null) {
            var oldTokens = verificationTokenRepository.findByUserAndTokenTypeAndUsedFalse(user, "PASSWORD_RESET");
            for (VerificationTokenEntity t : oldTokens) {
                t.setUsed(true);
                verificationTokenRepository.save(t);
            }

            String otpCode = String.format("%06d", new java.security.SecureRandom().nextInt(1000000));
            LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15);

            VerificationTokenEntity vToken = VerificationTokenEntity.builder()
                    .user(user)
                    .token(otpCode)
                    .tokenType("PASSWORD_RESET")
                    .expiresAt(expiresAt)
                    .used(false)
                    .build();
            verificationTokenRepository.save(vToken);

            String resetLink = frontendUrl + "/#/password-reset?otp=" + otpCode;

            n8nWebhookService.triggerPasswordResetWebhook(
                    user.getEmail(),
                    user.getFullName(),
                    otpCode,
                    resetLink,
                    expiresAt,
                    getClientIp());

            try {
                emailService.sendPasswordResetEmail(user.getEmail(), otpCode);
            } catch (Exception e) {
                log.error("Fallback password reset email failed: {}", e.getMessage());
            }

            recordAudit(user, "PASSWORD_RESET_OTP_ISSUED",
                    "6-Digit OTP code issued for password reset (15 min expiry).");
        }
    }

    @Transactional
    public void confirmPasswordReset(String otpCode, String newPassword) {
        if (otpCode == null || otpCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Verification code (OTP) is required.");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long.");
        }

        VerificationTokenEntity vToken = verificationTokenRepository
                .findByTokenAndTokenType(otpCode.trim(), "PASSWORD_RESET")
                .orElseThrow(() -> new IllegalArgumentException("Invalid or non-existent 6-digit verification code."));

        if (Boolean.TRUE.equals(vToken.getUsed())) {
            throw new IllegalArgumentException("This 6-digit verification code has already been used.");
        }

        if (vToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Verification code has expired. Please request a new OTP code.");
        }

        UserEntity user = vToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setLockedUntil(null);
        user.setFailedLoginAttempts(0);
        userRepository.save(user);

        vToken.setUsed(true);
        verificationTokenRepository.save(vToken);

        // Invalidate all active sessions & refresh tokens
        var activeTokens = refreshTokenRepository.findByUserAndRevokedFalse(user);
        for (RefreshTokenEntity rt : activeTokens) {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        }

        recordAudit(user, "PASSWORD_RESET_COMPLETED",
                "Password updated via 6-digit OTP. All active sessions invalidated.");

        n8nWebhookService.triggerPasswordChangedWebhook(
                user.getEmail(),
                user.getFullName(),
                getClientIp(),
                getUserAgent());
    }

    @Transactional
    public void changePassword(String email, String oldPassword, String newPassword) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("Incorrect current password.");
        }

        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters long.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Revoke all refresh tokens
        var activeTokens = refreshTokenRepository.findByUserAndRevokedFalse(user);
        for (RefreshTokenEntity rt : activeTokens) {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        }

        recordAudit(user, "PASSWORD_CHANGED", "Password changed while logged in. All other sessions invalidated.");

        n8nWebhookService.triggerPasswordChangedWebhook(
                user.getEmail(),
                user.getFullName(),
                getClientIp(),
                getUserAgent());
    }

    @Transactional
    public UserResponse updateProfile(String email, String fullName, String avatarUrl) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
        if (fullName != null) {
            user.setFullName(fullName.trim());
        }
        if (avatarUrl != null) {
            user.setAvatarUrl(avatarUrl.trim());
        }
        user = userRepository.save(user);
        recordAudit(user, "PROFILE_UPDATED", "Profile updated.");
        return mapToUserResponse(user);
    }

    @Transactional
    public UserResponse linkOAuthAccount(String email, String provider, String providerUserId, String username,
            String fullName, String avatarUrl) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));

        var existingLink = oauthAccountRepository.findByProviderAndProviderUserId(provider, providerUserId);
        if (existingLink.isPresent()) {
            if (existingLink.get().getUser().getId().equals(user.getId())) {
                return mapToUserResponse(user);
            }
            throw new IllegalArgumentException(provider + " account is already linked to another user.");
        }

        OAuthAccountEntity linkage = OAuthAccountEntity.builder()
                .user(user)
                .provider(provider)
                .providerUserId(providerUserId)
                .build();
        oauthAccountRepository.save(linkage);

        if (user.getAvatarUrl() == null || user.getAvatarUrl().isEmpty()) {
            user.setAvatarUrl(avatarUrl);
        }
        if (user.getFullName() == null || user.getFullName().isEmpty()) {
            user.setFullName(fullName);
        }
        userRepository.save(user);

        recordAudit(user, "OAUTH_LINKED", "Linked " + provider + " account.");
        n8nWebhookService.triggerOAuthLinkedWebhook(user.getEmail(), user.getFullName(), provider);

        return mapToUserResponse(user);
    }

    @Transactional
    public UserResponse unlinkOAuthAccount(String email, String provider) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));

        var linkage = oauthAccountRepository.findByUserAndProvider(user, provider)
                .orElseThrow(() -> new IllegalArgumentException("Provider " + provider + " is not connected."));

        long linkCount = oauthAccountRepository.findByUser(user).size();
        if (linkCount <= 1 && (user.getPassword() == null || user.getPassword().isEmpty()
                || user.getProvider().equals(provider))) {
            throw new IllegalArgumentException(
                    "Cannot disconnect account. You must set a password or connect another login method first.");
        }

        oauthAccountRepository.delete(linkage);
        if (user.getProvider().equals(provider)) {
            var remainingLinks = oauthAccountRepository.findByUser(user);
            if (!remainingLinks.isEmpty()) {
                user.setProvider(remainingLinks.get(0).getProvider());
            } else {
                user.setProvider("email");
            }
            userRepository.save(user);
        }

        recordAudit(user, "OAUTH_UNLINKED", "Unlinked " + provider + " account.");
        return mapToUserResponse(user);
    }

    @Transactional
    public TokenResponse completeOAuthRegistration(String email, String provider, String providerUserId,
            String username, String fullName, String avatarUrl) {
        email = email.trim().toLowerCase();
        UserEntity user;
        boolean isNewUser = false;

        var existingUserOpt = userRepository.findByEmail(email);
        if (existingUserOpt.isPresent()) {
            user = existingUserOpt.get();
            log.info("Linked existing user {} to OAuth registration", email);
        } else {
            isNewUser = true;
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
                    .fullName(fullName)
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

            try {
                emailService.sendWelcomeEmail(email, user.getUsername());
            } catch (Exception e) {
                log.error("Welcome email failed: {}", e.getMessage());
            }
        }

        var existingLink = oauthAccountRepository.findByProviderAndProviderUserId(provider, providerUserId);
        if (existingLink.isEmpty()) {
            OAuthAccountEntity linkage = OAuthAccountEntity.builder()
                    .user(user)
                    .provider(provider)
                    .providerUserId(providerUserId)
                    .build();
            oauthAccountRepository.save(linkage);
        }

        user.setLoginCount((user.getLoginCount() != null ? user.getLoginCount() : 0) + 1);
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        recordLoginHistory(user, email, true, null);
        recordAudit(user, "OAUTH_LOGIN", "Logged in via OAuth provider: " + provider);

        n8nWebhookService.triggerLoginSuccessWebhook(
                user.getId().toString(),
                user.getFullName(),
                user.getEmail(),
                provider,
                user.getAvatarUrl(),
                isNewUser,
                getClientIp(),
                getUserAgent());

        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getEmail(),
                user.getRole(),
                user.getId().toString(),
                provider,
                user.getUsername());
        String rawRefreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail());

        RefreshTokenEntity refreshTokenEntity = RefreshTokenEntity.builder()
                .user(user)
                .tokenHash(hashToken(rawRefreshToken))
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshTokenEntity);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .build();
    }

    private UserResponse mapToUserResponse(UserEntity user) {
        java.util.List<String> connected = oauthAccountRepository.findByUser(user).stream()
                .map(OAuthAccountEntity::getProvider)
                .toList();

        return UserResponse.builder()
                .id(user.getId().toString())
                .email(user.getEmail())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole())
                .isActive(user.getIsActive() != null ? user.getIsActive() : true)
                .avatarUrl(user.getAvatarUrl())
                .provider(user.getProvider())
                .loginCount(user.getLoginCount() != null ? user.getLoginCount() : 0)
                .lastLogin(user.getLastLogin() != null ? user.getLastLogin().toString() : null)
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : null)
                .connectedAccounts(connected)
                .build();
    }
}
