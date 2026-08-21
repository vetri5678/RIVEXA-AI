package ai.riskvision.graveyard.config;

import ai.riskvision.graveyard.service.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT authentication filter for RiskVision AI.
 *
 * Token extraction order:
 *  1. Authorization: Bearer <token> header (primary method)
 *  2. access_token cookie (fallback for SSR/browser contexts)
 *
 * Security hardening:
 *  - Uses validateAccessToken() — rejects refresh tokens presented as access tokens
 *  - Skips authentication if context already populated (idempotent)
 *  - Catches UsernameNotFoundException separately to avoid leaking user existence
 *  - Logs at DEBUG for token parse failures (no sensitive data in logs)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            path = request.getRequestURI();
        }
        return path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/register")
                || path.equals("/api/v1/auth/refresh")
                || path.equals("/api/v1/auth/logout")
                || path.equals("/api/v1/auth/verify-email")
                || path.equals("/api/v1/auth/resend-verification")
                || path.startsWith("/api/v1/auth/password-reset")
                || path.startsWith("/api/v1/auth/github")
                || path.startsWith("/api/v1/auth/oauth2")
                || path.startsWith("/api/v1/oauth2")
                || path.startsWith("/login/oauth2")
                || path.startsWith("/oauth2")
                || path.equals("/api/v1/health")
                || path.equals("/api/github/health")
                || path.equals("/api/v1/github/health")
                || path.startsWith("/api/v1/pipeline")
                || path.startsWith("/ws/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/assets/")
                || path.startsWith("/static/")
                || path.equals("/favicon.ico")
                || path.equals("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
        throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                // Strict validation: only accept tokens with type="access"
                if (jwtTokenProvider.validateAccessToken(token)) {
                    String username = jwtTokenProvider.getUsername(token);
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("JWT authentication set for user: {}", username);
                } else {
                    log.debug("JWT access token validation failed for request: {}",
                        request.getRequestURI());
                }
            } catch (UsernameNotFoundException e) {
                // User deleted after token was issued — do not set authentication
                log.warn("JWT token valid but user not found (may have been deleted): {}",
                    e.getMessage());
            } catch (Throwable e) {
                // Never propagate — just skip authentication for this request
                log.error("JWT filter error for request {}: {}", request.getRequestURI(),
                    e.getMessage(), e);
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the JWT from the request.
     * Priority: Authorization header > access_token cookie
     */
    private String extractToken(HttpServletRequest request) {
        // 1. Authorization header (preferred)
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            if (!token.isEmpty()) {
                return token;
            }
        }

        // 2. Cookie fallback (for browser-based requests)
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("access_token".equals(cookie.getName())) {
                    String token = cookie.getValue();
                    if (token != null && !token.trim().isEmpty()) {
                        return token.trim();
                    }
                }
            }
        }

        return null;
    }
}
