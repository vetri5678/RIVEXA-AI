package ai.riskvision.graveyard.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2FailureHandler implements AuthenticationFailureHandler {

    private final HttpCookieOAuth2AuthorizationRequestRepository httpCookieOAuth2AuthorizationRequestRepository;

    @Value("${app.frontend.url:http://localhost:8080}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException, ServletException {
        String uri = request.getRequestURI();
        boolean isGitHub = (uri != null && uri.contains("github")) || (exception.getMessage() != null && exception.getMessage().toLowerCase().contains("github"));

        log.error("[OAUTH FAILURE HANDLER] OAuth2 authentication failure for URI {}: {}", uri, exception.getMessage(), exception);

        String errorMsg = exception.getLocalizedMessage();
        if (errorMsg == null) errorMsg = exception.getMessage();

        String lowerError = errorMsg != null ? errorMsg.toLowerCase() : "";

        if (lowerError.contains("access_denied") || lowerError.contains("user_denied")) {
            errorMsg = (isGitHub ? "GitHub" : "Google") + " authorization request was cancelled by the user.";
        } else if (lowerError.contains("redirect_uri_mismatch") || lowerError.contains("redirect_uri")) {
            errorMsg = (isGitHub ? "GitHub" : "Google") + " OAuth Redirect URI mismatch. Please verify Authorized Callback URL in developer settings.";
        } else if (lowerError.contains("authorization_request_not_found") || lowerError.contains("state")) {
            errorMsg = (isGitHub ? "GitHub" : "Google") + " OAuth session state validation failed or expired. Please try again.";
        } else if (lowerError.contains("invalid_code") || lowerError.contains("code_expired") || lowerError.contains("bad_verification_code")) {
            errorMsg = (isGitHub ? "GitHub" : "Google") + " authorization code expired or invalid. Please try logging in again.";
        } else if (lowerError.contains("invalid_client") || lowerError.contains("client_id") || lowerError.contains("unconfigured")) {
            if (isGitHub) {
                errorMsg = "GitHub OAuth App credentials are not configured. Please set GITHUB_CLIENT_ID and GITHUB_CLIENT_SECRET in your .env file.";
            } else {
                errorMsg = "Google OAuth credentials are not configured. Please set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET in your .env file.";
            }
        } else if (lowerError.contains("rollback-only") || lowerError.contains("transaction silently rolled back")) {
            errorMsg = "Account linking conflict occurred. Please ensure this social account is not already linked to another user.";
        } else if (errorMsg == null || errorMsg.trim().isEmpty() || lowerError.contains("filter execution")) {
            errorMsg = (isGitHub ? "GitHub" : "Google") + " authentication failed. Please check network connectivity and try again.";
        }

        String baseUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        if (baseUrl.endsWith("/dashboard")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - "/dashboard".length());
        }

        httpCookieOAuth2AuthorizationRequestRepository.removeAuthorizationRequestCookies(request, response);

        String targetUrl = baseUrl + "/#/login?error=" + URLEncoder.encode(errorMsg, StandardCharsets.UTF_8);
        log.info("[GITHUB-OAUTH] Redirecting failed OAuth user to: {}", targetUrl);
        if (!response.isCommitted()) {
            response.sendRedirect(targetUrl);
        }
    }
}
