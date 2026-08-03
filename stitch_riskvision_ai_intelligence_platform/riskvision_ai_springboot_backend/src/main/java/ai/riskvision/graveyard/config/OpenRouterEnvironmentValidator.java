package ai.riskvision.graveyard.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OpenRouterEnvironmentValidator {

    private final OpenRouterConfiguration openRouterConfiguration;

    @PostConstruct
    public void validateEnvironment() {
        String apiKey = openRouterConfiguration.getApiKey();
        String baseUrl = openRouterConfiguration.getBaseUrl();
        String defaultModel = openRouterConfiguration.getDefaultModel();

        log.info("==================================================================");
        log.info("           RiskVision AI — OpenRouter Service Initialization      ");
        log.info("==================================================================");
        log.info("provider       : OpenRouter");
        log.info("model          : {}", defaultModel);
        log.info("Base URL       : {}", baseUrl);
        log.info("Fallback Models: {}", openRouterConfiguration.getFallbackModels());

        if (apiKey == null || apiKey.isBlank() || apiKey.contains("placeholder") || apiKey.contains("change-me")) {
            log.error("apiKey         : MISSING ✗");
            log.error("🚨 [OPENROUTER ERROR] OPENROUTER_API_KEY is MISSING or set to placeholder value!");
            log.error("🚨 AI Copilot features will not function until a valid key is provided in .env!");
        } else {
            String maskedKey = apiKey.length() > 12 
                    ? apiKey.substring(0, 10) + "..." + apiKey.substring(apiKey.length() - 4)
                    : "PRESENT";
            log.info("apiKey         : Loaded ✓ (masked: {})", maskedKey);
            log.info("✅ OpenRouter AI Configuration successfully validated!");
        }
        log.info("==================================================================");
    }
}

