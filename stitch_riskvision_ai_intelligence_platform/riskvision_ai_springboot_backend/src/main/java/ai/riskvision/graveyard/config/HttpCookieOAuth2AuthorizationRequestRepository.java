package ai.riskvision.graveyard.config;

import ai.riskvision.graveyard.util.CookieUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

@Component
public class HttpCookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME = "oauth2_auth_request";
    public static final String REDIRECT_URI_PARAM_COOKIE_NAME = "redirect_uri";
    public static final String INITIATING_USER_COOKIE_NAME = "rivexa_initiating_user";
    private static final int COOKIE_EXPIRE_SECONDS = 180;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return CookieUtils.getCookie(request, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME)
                .map(cookie -> CookieUtils.deserialize(cookie, OAuth2AuthorizationRequest.class))
                .orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        if (authorizationRequest == null) {
            removeAuthorizationRequestCookies(request, response);
            return;
        }

        CookieUtils.addCookie(
                response,
                OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME,
                CookieUtils.serialize(authorizationRequest),
                COOKIE_EXPIRE_SECONDS
        );

        String redirectUriAfterLogin = request.getParameter(REDIRECT_URI_PARAM_COOKIE_NAME);
        if (redirectUriAfterLogin != null && !redirectUriAfterLogin.trim().isEmpty()) {
            CookieUtils.addCookie(
                    response,
                    REDIRECT_URI_PARAM_COOKIE_NAME,
                    redirectUriAfterLogin,
                    COOKIE_EXPIRE_SECONDS
            );
        }

        String uri = request.getRequestURI();
        boolean isGoogleRequest = uri != null && uri.toLowerCase().contains("google");
        boolean isGitHubRequest = uri != null && uri.toLowerCase().contains("github");

        if (isGoogleRequest) {
            // Google login is strictly Application Authentication. Delete any residual initiating user cookie.
            CookieUtils.deleteCookie(request, response, INITIATING_USER_COOKIE_NAME);
            return;
        }

        String initiatingUser = null;
        if (isGitHubRequest) {
            initiatingUser = request.getParameter("user_email");
            if (initiatingUser == null || initiatingUser.trim().isEmpty()) {
                initiatingUser = request.getParameter("userId");
            }
            if (initiatingUser == null || initiatingUser.trim().isEmpty()) {
                initiatingUser = request.getParameter("email");
            }
            if (initiatingUser == null || initiatingUser.trim().isEmpty()) {
                initiatingUser = request.getParameter("user");
            }

            // If an authenticated principal exists, use it as the initiating user for GitHub integration
            if ((initiatingUser == null || initiatingUser.trim().isEmpty()) && request.getUserPrincipal() != null) {
                initiatingUser = request.getUserPrincipal().getName();
            }

            if (initiatingUser != null && !initiatingUser.trim().isEmpty()) {
                CookieUtils.addCookie(
                        response,
                        INITIATING_USER_COOKIE_NAME,
                        initiatingUser.trim(),
                        COOKIE_EXPIRE_SECONDS
                );
            } else {
                // Unauthenticated GitHub sign-in attempt: clear any residual initiating user cookie
                CookieUtils.deleteCookie(request, response, INITIATING_USER_COOKIE_NAME);
            }
        }
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest authorizationRequest = this.loadAuthorizationRequest(request);
        removeAuthorizationRequestCookies(request, response);
        return authorizationRequest;
    }

    public void removeAuthorizationRequestCookies(HttpServletRequest request, HttpServletResponse response) {
        CookieUtils.deleteCookie(request, response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        CookieUtils.deleteCookie(request, response, REDIRECT_URI_PARAM_COOKIE_NAME);
        // Do NOT delete INITIATING_USER_COOKIE_NAME here so CustomOAuth2SuccessHandler can read it during OAuth callback.
    }

    public void removeInitiatingUserCookie(HttpServletRequest request, HttpServletResponse response) {
        CookieUtils.deleteCookie(request, response, INITIATING_USER_COOKIE_NAME);
    }
}
