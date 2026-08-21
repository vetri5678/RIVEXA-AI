package ai.riskvision.graveyard.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 6 / Boot 3 security configuration.
 *
 * Key hardening applied:
 *  - Stateless session (STATELESS) — no HTTP session for API calls
 *  - Security headers: CSP, HSTS, X-Frame-Options, X-Content-Type-Options, Referrer-Policy
 *  - Actuator endpoints restricted to ADMIN role only
 *  - CORS restricted to configured allowed origins (no wildcard in production)
 *  - @EnableMethodSecurity for @PreAuthorize/@PostAuthorize on controllers
 *  - CSRF disabled (JWT-based stateless API)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomOAuth2SuccessHandler customOAuth2SuccessHandler;
    private final CustomOAuth2FailureHandler customOAuth2FailureHandler;
    private final HttpCookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository;
    private final CustomOAuth2AuthorizationRequestResolver customOAuth2AuthorizationRequestResolver;

    /**
     * Comma-separated allowed origins loaded from CORS_ALLOWED_ORIGINS environment variable.
     * Defaults to localhost development origins. Override in production.
     */
    @Value("${spring.web.cors.allowed-origins:http://localhost:8080,http://127.0.0.1:8080,http://localhost:5173,http://127.0.0.1:5173,http://localhost:5176,http://127.0.0.1:5176}")
    private String corsAllowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            org.springframework.security.web.AuthenticationEntryPoint customAuthenticationEntryPoint,
            org.springframework.security.web.access.AccessDeniedHandler customAccessDeniedHandler) throws Exception {
        http
            // ── CORS ─────────────────────────────────────────────────────────
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // ── CSRF: Disabled — API is stateless JWT, no form login ─────────
            .csrf(csrf -> csrf.disable())

            // ── Session: Stateless — JWT carries all auth state ──────────────
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── Security Headers ─────────────────────────────────────────────
            .headers(headers -> headers
                // Prevents clickjacking
                .frameOptions(frame -> frame.deny())
                // Prevents MIME-type sniffing
                .contentTypeOptions(cto -> {})
                // HTTP Strict Transport Security (only effective over HTTPS)
                .httpStrictTransportSecurity(hsts -> hsts
                    .maxAgeInSeconds(31536000)
                    .includeSubDomains(true)
                    .preload(true))
                // Content Security Policy
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives(
                        "default-src 'self'; " +
                        "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                        "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                        "font-src 'self' https://fonts.gstatic.com; " +
                        "img-src 'self' data: https:; " +
                        "connect-src 'self' ws: wss:; " +
                        "frame-ancestors 'none'; " +
                        "object-src 'none';"
                    ))
                // Referrer Policy
                .referrerPolicy(referrer -> referrer
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            )

            // ── Exception Handling ───────────────────────────────────────────
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(customAuthenticationEntryPoint)
                .accessDeniedHandler(customAccessDeniedHandler))

            // ── Authorization Rules ──────────────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                // ── Public endpoints ────────────────────────────────────────
                .requestMatchers(
                    // Static frontend assets
                    "/", "/index.html", "/favicon.ico", "/favicon.svg", "/icons.svg",
                    "/assets/**", "/static/**",
                    "/*.js", "/*.css", "/*.svg", "/*.png", "/*.jpg", "/*.jpeg",
                    "/*.woff", "/*.woff2", "/*.ttf",
                    // Frontend SPA routes
                    "/dashboard", "/projects", "/profile", "/settings",
                    "/reports", "/login", "/register",
                    // Auth endpoints (public)
                    "/api/v1/auth/login",
                    "/api/v1/auth/register",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/logout",
                    "/api/v1/auth/verify-email",
                    "/api/v1/auth/resend-verification",
                    "/api/v1/auth/password-reset",
                    "/api/v1/auth/password-reset/confirm",
                    // OAuth2 callbacks
                    "/login/oauth2/**",
                    "/oauth2/**",
                    // Health check (public for load balancers)
                    "/api/v1/health",
                    "/api/github/health",
                    "/api/v1/github/health",
                    // Pipeline (internal ML service)
                    "/api/v1/pipeline/**",
                    // WebSocket
                    "/ws/**",
                    // Error
                    "/error"
                ).permitAll()

                // ── Actuator: ADMIN only ────────────────────────────────────
                .requestMatchers("/actuator/**").hasRole("ADMIN")

                // ── Test email: ADMIN only (remove in production) ───────────
                .requestMatchers("/api/v1/auth/test-email").hasRole("ADMIN")

                // ── Login history: ADMIN only ─────────────────────────
                .requestMatchers("/api/v1/auth/login-history").hasRole("ADMIN")

                // ── Audit logs and admin APIs: ADMIN only ────────────────────
                .requestMatchers("/api/v1/audit/**", "/api/admin/**").hasRole("ADMIN")

                // ── User management: ADMIN/MANAGER only ───────────────────
                .requestMatchers("/api/v1/users/**").hasAnyRole("ADMIN", "MANAGER")

                // ── Authenticated endpoints ─────────────────────────────────
                .requestMatchers(
                    "/api/v1/repositories/**",
                    "/api/v1/predictions/**",
                    "/api/v1/projects/**",
                    "/api/v1/me",
                    "/api/v1/profile/**",
                    "/api/v1/auth/me",
                    "/api/v1/auth/change-password",
                    "/api/v1/auth/oauth2/**",
                    "/api/v1/telemetry/**",
                    "/api/v1/dashboard/**",
                    "/api/v1/github/**",
                    "/api/github/**",
                    "/api/v1/ai/**",
                    "/api/v1/reports/**",
                    "/api/v1/files/**",
                    "/api/v1/ml/**"
                ).authenticated()

                // ── Everything else requires authentication ─────────────────
                .anyRequest().authenticated()
            )

            // ── OAuth2 Login ─────────────────────────────────────────────────
            .oauth2Login(oauth -> oauth
                .loginPage("/login")
                .authorizationEndpoint(authorization -> authorization
                    .authorizationRequestRepository(cookieAuthorizationRequestRepository)
                    .authorizationRequestResolver(customOAuth2AuthorizationRequestResolver))
                .redirectionEndpoint(redirection -> redirection
                    .baseUri("/login/oauth2/code/*"))
                .successHandler(customOAuth2SuccessHandler)
                .failureHandler(customOAuth2FailureHandler))

            // ── JWT Filter ───────────────────────────────────────────────────
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS configuration.
     * Reads allowed origins from environment variable CORS_ALLOWED_ORIGINS.
     * No wildcard origins are permitted in any environment.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Parse comma-separated origins from env var — never allow wildcard
        List<String> origins = Arrays.stream(corsAllowedOrigins.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty() && !s.equals("*"))
            .toList();

        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        // Specific headers instead of wildcard for tighter security
        config.setAllowedHeaders(List.of(
            "Authorization", "Content-Type", "Accept", "Origin",
            "X-Requested-With", "Cache-Control", "X-Request-ID"
        ));
        config.setExposedHeaders(List.of("X-Total-Count", "X-Request-ID"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L); // Pre-flight cache

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public org.springframework.security.web.AuthenticationEntryPoint customAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write(
                "{\"success\":false,\"status\":401,\"message\":\"" + authException.getMessage() + "\"}"
            );
        };
    }

    @Bean
    public org.springframework.security.web.access.AccessDeniedHandler customAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write(
                "{\"success\":false,\"status\":403,\"message\":\"You do not have permission to access this resource\"}"
            );
        };
    }
}
