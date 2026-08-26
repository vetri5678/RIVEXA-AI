package ai.riskvision.graveyard.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CustomOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final OAuth2AuthorizationRequestResolver defaultResolver;

    public CustomOAuth2AuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        this.defaultResolver = new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, "/oauth2/authorization");
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        OAuth2AuthorizationRequest authorizationRequest = defaultResolver.resolve(request);
        return checkAndValidate(request, authorizationRequest);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        OAuth2AuthorizationRequest authorizationRequest = defaultResolver.resolve(request, clientRegistrationId);
        return checkAndValidate(request, authorizationRequest);
    }

    private OAuth2AuthorizationRequest checkAndValidate(HttpServletRequest request, OAuth2AuthorizationRequest authorizationRequest) {
        if (authorizationRequest == null) {
            return null;
        }

        String clientId = authorizationRequest.getClientId();
        String uri = request.getRequestURI();
        String provider = uri.substring(uri.lastIndexOf('/') + 1);

        log.info("[STAGE 1: OAUTH_REQUEST_INITIATED] Intercepted OAuth request for provider='{}', URI='{}'", provider, uri);
        log.info("[STAGE 2: AUTHORIZATION_RESOLVER_INTERCEPT] Validating client registration. Client ID configured: {}", (clientId != null && !isPlaceholderClientId(clientId)));

        if (isPlaceholderClientId(clientId)) {
            log.warn("[STAGE 2: AUTHORIZATION_RESOLVER_INTERCEPT] OAuth authorization attempt for provider '{}' intercepted: Client ID is set to placeholder '{}'.", provider, clientId);
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    "unconfigured_" + provider + "_client",
                    provider.toUpperCase() + " OAuth App credentials are not configured. Please set " + provider.toUpperCase() + "_CLIENT_ID and " + provider.toUpperCase() + "_CLIENT_SECRET in your .env file.",
                    null
            ));
        }

        // GitHub OAuth parameter resolution:
        // GitHub supports prompt=consent to force re-authorization.
        // GitHub does NOT support prompt=select_account (which causes GitHub's authorization endpoint
        // to fail session state resolution and redirect to /sessions/verified-device returning 404).
        if ("github".equalsIgnoreCase(provider)) {
            String promptReq = request.getParameter("prompt");
            if ("select_account".equalsIgnoreCase(promptReq) || "true".equalsIgnoreCase(request.getParameter("force_prompt"))) {
                log.info("[STAGE 2: AUTHORIZATION_RESOLVER_INTERCEPT] GitHub OAuth request — mapping prompt=select_account to prompt=consent for GitHub compatibility.");
                OAuth2AuthorizationRequest.Builder builder = OAuth2AuthorizationRequest.from(authorizationRequest);
                builder.additionalParameters(params -> params.put("prompt", "consent"));
                return builder.build();
            } else {
                log.info("[STAGE 2: AUTHORIZATION_RESOLVER_INTERCEPT] GitHub OAuth request — using standard GitHub authorization flow.");
            }
        } else if ("google".equalsIgnoreCase(provider)) {
            String promptReq = request.getParameter("prompt");
            if (promptReq == null || "select_account".equalsIgnoreCase(promptReq)) {
                log.info("[STAGE 2: AUTHORIZATION_RESOLVER_INTERCEPT] Google OAuth request — setting prompt=select_account.");
                OAuth2AuthorizationRequest.Builder builder = OAuth2AuthorizationRequest.from(authorizationRequest);
                builder.additionalParameters(params -> params.put("prompt", "select_account"));
                return builder.build();
            }
        }

        return authorizationRequest;
    }

    private boolean isPlaceholderClientId(String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            return true;
        }
        String lower = clientId.trim().toLowerCase();
        return lower.startsWith("mock-") ||
               lower.startsWith("your-actual-") ||
               lower.contains("placeholder") ||
               lower.contains("change-me") ||
               lower.equals("your_github_client_id") ||
               lower.equals("your_google_client_id");
    }
}
