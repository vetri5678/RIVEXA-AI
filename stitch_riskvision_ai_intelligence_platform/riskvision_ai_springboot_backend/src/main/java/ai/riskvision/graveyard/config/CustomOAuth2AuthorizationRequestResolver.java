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

        if (isPlaceholderClientId(clientId)) {
            log.warn("OAuth authorization attempt for provider '{}' intercepted: Client ID is set to placeholder '{}'. Redirecting to error handler.", provider, clientId);
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    "unconfigured_" + provider + "_client",
                    provider.toUpperCase() + " OAuth App credentials are not configured. Please set " + provider.toUpperCase() + "_CLIENT_ID and " + provider.toUpperCase() + "_CLIENT_SECRET in your .env file.",
                    null
            ));
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
