package ai.riskvision.graveyard.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Controller
@Slf4j
public class CustomErrorController implements ErrorController {

    @Value("${app.frontend.url:http://localhost:8080}")
    private String frontendUrl;

    @RequestMapping("/error")
    public Object handleError(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Object statusObj = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object exceptionObj = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        Object requestUriObj = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);

        int statusCode = statusObj != null ? Integer.parseInt(statusObj.toString()) : 500;
        String requestUri = requestUriObj != null ? requestUriObj.toString() : "";
        Throwable exception = exceptionObj instanceof Throwable ? (Throwable) exceptionObj : null;

        String errorMessage = exception != null ? exception.getMessage() : "An unexpected server error occurred";
        log.error("CustomErrorController caught error: status={}, uri={}, exception={}", statusCode, requestUri, errorMessage);

        String baseUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        if (baseUrl.endsWith("/dashboard")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - "/dashboard".length());
        }

        boolean isOAuthRequest = requestUri.contains("/oauth2/") || requestUri.contains("/login/oauth2/") || 
                                (errorMessage != null && errorMessage.toLowerCase().contains("oauth"));

        if (isOAuthRequest) {
            String redirectUrl = baseUrl + "/#/login?error=" + URLEncoder.encode(errorMessage, StandardCharsets.UTF_8);
            log.info("Redirecting OAuth error to frontend: {}", redirectUrl);
            response.sendRedirect(redirectUrl);
            return null;
        }

        String acceptHeader = request.getHeader("Accept");
        boolean requestsJson = (acceptHeader != null && acceptHeader.contains("application/json")) || requestUri.startsWith("/api/");

        if (requestsJson) {
            HttpStatus status = HttpStatus.resolve(statusCode);
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            return ResponseEntity.status(status).body(Map.of(
                    "status", statusCode,
                    "error", status.getReasonPhrase(),
                    "message", errorMessage,
                    "timestamp", System.currentTimeMillis()
            ));
        }

        String targetUrl = baseUrl + "/#/login?error=" + URLEncoder.encode(errorMessage, StandardCharsets.UTF_8);
        response.sendRedirect(targetUrl);
        return null;
    }
}
