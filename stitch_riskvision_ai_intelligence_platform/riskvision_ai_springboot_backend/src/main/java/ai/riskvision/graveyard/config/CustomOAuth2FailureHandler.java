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

        log.error("OAuth2 authentication failure: {}", exception.getMessage());

        String errorMsg = exception.getLocalizedMessage();
        String uri = request.getRequestURI();
        boolean isGitHub = (uri != null && uri.contains("github")) || (errorMsg != null && errorMsg.toLowerCase().contains("github"));

        if (errorMsg == null || errorMsg.trim().isEmpty()) {
            errorMsg = "OAuth authentication failed";
        } else if (errorMsg.contains("invalid_client") || errorMsg.contains("client_id") || errorMsg.contains("unconfigured")) {
            if (isGitHub) {
                errorMsg = "GitHub OAuth App credentials are not configured. Please set GITHUB_CLIENT_ID and GITHUB_CLIENT_SECRET in your .env file.";
            } else {
                errorMsg = "Google OAuth 401 Invalid Client: Ensure GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET match your Google Cloud Console credentials.";
            }
        }

        String baseUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        if (baseUrl.endsWith("/dashboard")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - "/dashboard".length());
        }

        httpCookieOAuth2AuthorizationRequestRepository.removeAuthorizationRequestCookies(request, response);

        String targetUrl = baseUrl + "/#/login?error=" + URLEncoder.encode(errorMsg, StandardCharsets.UTF_8);
        log.info("Redirecting failed OAuth user to: {}", targetUrl);
        response.sendRedirect(targetUrl);
    }
}
