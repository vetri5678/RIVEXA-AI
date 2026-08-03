package ai.riskvision.graveyard.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.Optional;

/**
 * JWT token lifecycle management for RiskVision AI.
 *
 * Security measures:
 *  - HMAC-SHA256 signing with a minimum 256-bit (32-byte) key
 *  - Distinct "type" claim enforced on validation ("access" vs "refresh")
 *  - Secret key validated at startup — application refuses to start without it
 *  - Full Claims extraction helper for use in filters and services
 *  - Token expiry validated by jjwt parser (not manually)
 */
@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secret:${SECRET_KEY:}}")
    private String secretKey;

    /** Access token validity: default 30 minutes (1 800 000 ms). */
    @Value("${jwt.expire-length:1800000}")
    private long accessTokenValidityMs;

    /** Refresh token validity: default 7 days. Configurable via JWT_REFRESH_EXPIRE_LENGTH. */
    @Value("${jwt.refresh-expire-length:604800000}")
    private long refreshTokenValidityMs;

    private Key signingKey;

    @PostConstruct
    protected void init() {
        if (secretKey == null || secretKey.trim().isEmpty()) {
            throw new IllegalStateException(
                "JWT secret key is not configured. " +
                "Set the SECRET_KEY environment variable to a minimum 64-character random hex string. " +
                "Generate one with: openssl rand -hex 64");
        }
        byte[] keyBytes = secretKey.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                "JWT secret key is too short (" + keyBytes.length + " bytes). " +
                "Minimum required is 32 bytes (256 bits). " +
                "Generate a secure key with: openssl rand -hex 64");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("JwtTokenProvider initialised. Access token TTL={}ms, Refresh token TTL={}ms",
            accessTokenValidityMs, refreshTokenValidityMs);
    }

    // ─── Token Generation ────────────────────────────────────────────────────

    /**
     * Generate an access token with full user claims.
     *
     * @param email      User's email (JWT subject)
     * @param role       User's role (e.g. "admin", "viewer")
     * @param userId     UUID string of the user
     * @param provider   Auth provider ("email", "google", "github")
     * @param username   Username / display name
     */
    public String generateAccessToken(String email, String role, String userId,
                                      String provider, String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenValidityMs);

        Claims claims = Jwts.claims().setSubject(email);
        claims.put("type", "access");
        claims.put("role", role);
        claims.put("userId", userId);
        claims.put("email", email);
        claims.put("provider", provider != null ? provider : "email");
        claims.put("username", username != null ? username : email);
        claims.put("authorities", java.util.List.of("ROLE_" + (role != null ? role.toUpperCase() : "VIEWER")));

        return Jwts.builder()
            .setClaims(claims)
            .setIssuedAt(now)
            .setExpiration(expiry)
            .signWith(signingKey, SignatureAlgorithm.HS256)
            .compact();
    }

    /**
     * @deprecated Use {@link #generateAccessToken(String, String, String, String, String)} directly.
     * Kept for backward-compatibility with existing call sites in AuthService and OAuth2 handlers.
     */
    @Deprecated(forRemoval = false)
    public String generateToken(String username, String role) {
        return generateAccessToken(username, role, null, "email", username);
    }

    /**
     * @deprecated Use {@link #generateAccessToken(String, String, String, String, String)} directly.
     */
    @Deprecated(forRemoval = false)
    public String generateToken(String username, String role, String userId, String email) {
        return generateAccessToken(email != null ? email : username, role, userId, "email", username);
    }

    /**
     * @deprecated Use {@link #generateAccessToken(String, String, String, String, String)} directly.
     */
    @Deprecated(forRemoval = false)
    public String generateToken(String username, String role, String userId, String email,
                                String provider, String userNick) {
        return generateAccessToken(email != null ? email : username, role, userId, provider, userNick);
    }

    /**
     * Generate a refresh token (minimal claims — only subject and type).
     *
     * @param email User's email (subject)
     */
    public String generateRefreshToken(String email) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshTokenValidityMs);

        Claims claims = Jwts.claims().setSubject(email);
        claims.put("type", "refresh");

        return Jwts.builder()
            .setClaims(claims)
            .setIssuedAt(now)
            .setExpiration(expiry)
            .signWith(signingKey, SignatureAlgorithm.HS256)
            .compact();
    }

    // ─── Token Parsing ───────────────────────────────────────────────────────

    /**
     * Parse and return all claims from a token.
     *
     * @throws JwtException if the token is invalid, expired, or tampered.
     */
    public Claims getClaims(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(signingKey)
            .build()
            .parseClaimsJws(token)
            .getBody();
    }

    /**
     * Get the subject (email) from a token.
     * The token's signature and expiry are validated as a side effect.
     */
    public String getUsername(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * Return the optional "type" claim ("access" or "refresh").
     */
    public Optional<String> getTokenType(String token) {
        try {
            return Optional.ofNullable(getClaims(token).get("type", String.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // ─── Token Validation ────────────────────────────────────────────────────

    /**
     * Validate that the token is a valid, non-expired ACCESS token.
     * Rejects refresh tokens, expired tokens, and tampered tokens.
     */
    public boolean validateAccessToken(String token) {
        try {
            Claims claims = getClaims(token);
            String type = claims.get("type", String.class);
            if (!"access".equals(type)) {
                log.warn("Token type mismatch: expected 'access', got '{}'", type);
                return false;
            }
            return true;
        } catch (JwtException e) {
            log.debug("Access token validation failed: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Unexpected error validating access token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validate that the token is a valid, non-expired REFRESH token.
     */
    public boolean validateRefreshToken(String token) {
        try {
            Claims claims = getClaims(token);
            String type = claims.get("type", String.class);
            if (!"refresh".equals(type)) {
                log.warn("Token type mismatch: expected 'refresh', got '{}'", type);
                return false;
            }
            return true;
        } catch (JwtException e) {
            log.debug("Refresh token validation failed: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Unexpected error validating refresh token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Generic validation (accepts any valid signed, non-expired token).
     * Prefer {@link #validateAccessToken} or {@link #validateRefreshToken} for type-specific validation.
     */
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
