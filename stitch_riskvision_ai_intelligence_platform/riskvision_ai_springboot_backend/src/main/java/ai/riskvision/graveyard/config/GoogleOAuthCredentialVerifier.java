package ai.riskvision.graveyard.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Diagnostic utility that verifies Google OAuth 2.0 credential configuration at startup.
 * Prevents silent failures or generic HTTP 401 errors by logging clear, actionable instructions
 * if default mock values are detected.
 */
@Component
@Slf4j
public class GoogleOAuthCredentialVerifier implements CommandLineRunner {

    @Value("${spring.security.oauth2.client.registration.google.client-id:}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret:}")
    private String googleClientSecret;

    @Override
    public void run(String... args) throws Exception {
        log.info("Checking Google OAuth2 Client Configuration...");

        boolean isMockId = googleClientId == null 
                || googleClientId.trim().isEmpty() 
                || googleClientId.contains("mock-google-client-id") 
                || googleClientId.contains("your-actual-google-client-id");

        boolean isMockSecret = googleClientSecret == null 
                || googleClientSecret.trim().isEmpty() 
                || googleClientSecret.contains("mock-google-client-secret") 
                || googleClientSecret.contains("your-actual-google-client-secret");

        if (isMockId || isMockSecret) {
            log.warn("==========================================================================================");
            log.warn("🚨 [OAUTH DIAGNOSTIC WARNING] Google OAuth Credentials are NOT configured or use mock values!");
            log.warn("To enable Google Login on the platform, you must configure real Google Cloud OAuth credentials.");
            log.warn("Current Client ID: {}", googleClientId);
            log.warn("");
            log.warn("👉 Instructions:");
            log.warn("   1. Create an OAuth Web Client ID in Google Cloud Console (https://console.cloud.google.com)");
            log.warn("   2. Add Authorized JavaScript Origins: http://localhost:8080 and http://localhost:5176");
            log.warn("   3. Add Authorized Redirect URI: http://localhost:8080/login/oauth2/code/google");
            log.warn("   4. Set environment variables GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET or edit riskvision_ai_springboot_backend/.env");
            log.warn("==========================================================================================");
        } else {
            log.info("I Google OAuth2 client registered successfully (Client ID: {}).", googleClientId);
        }
    }
}
