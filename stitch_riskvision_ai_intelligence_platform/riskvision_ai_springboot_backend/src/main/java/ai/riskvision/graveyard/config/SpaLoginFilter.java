package ai.riskvision.graveyard.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Filter that intercepts direct requests to /login and /login?error before Spring Security's
 * DefaultLoginPageGeneratingFilter can render the default plain HTML login page.
 * Redirects or forwards requests to the React SPA login route (#/login).
 */
@Component
@Slf4j
public class SpaLoginFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String path = request.getRequestURI();
        
        // Intercept GET requests strictly to /login or /login/
        if ("GET".equalsIgnoreCase(request.getMethod()) && ("/login".equals(path) || "/login/".equals(path))) {
            String queryString = request.getQueryString();
            String errorMsg = request.getParameter("error");
            
            if (queryString != null && queryString.contains("error")) {
                if (errorMsg == null || errorMsg.trim().isEmpty() || "true".equalsIgnoreCase(errorMsg)) {
                    errorMsg = "Invalid credentials";
                }
                String redirectUrl = "/#/login?error=" + URLEncoder.encode(errorMsg, StandardCharsets.UTF_8);
                log.info("[SPA LOGIN FILTER] Intercepted /login?error request, redirecting to SPA route: {}", redirectUrl);
                response.sendRedirect(redirectUrl);
                return; // Stop filter chain execution so DefaultLoginPageGeneratingFilter never runs
            } else {
                // Forward /login to /index.html so the single-page application handles the view
                log.info("[SPA LOGIN FILTER] Intercepted direct /login request, forwarding to /index.html");
                request.getRequestDispatcher("/index.html").forward(request, response);
                return; // Stop filter chain execution
            }
        }

        filterChain.doFilter(request, response);
    }
}
