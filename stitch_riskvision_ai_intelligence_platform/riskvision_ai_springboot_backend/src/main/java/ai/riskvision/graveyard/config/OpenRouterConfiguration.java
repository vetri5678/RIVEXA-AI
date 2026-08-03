package ai.riskvision.graveyard.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.List;

@Configuration
@Getter
public class OpenRouterConfiguration {

    @Value("${openrouter.api-key}")
    private String apiKey;

    @Value("${openrouter.base-url:https://openrouter.ai/api/v1}")
    private String baseUrl;

    @Value("${openrouter.default-model:openrouter/free}")
    private String defaultModel;

    @Value("${openrouter.fallback-models:google/gemma-4-26b-a4b-it:free,openai/gpt-oss-20b:free,nvidia/nemotron-3-nano-30b-a3b:free}")
    private List<String> fallbackModels;

    @Value("${openrouter.timeout-seconds:30}")
    private int timeoutSeconds;

    @Value("${openrouter.max-retries:3}")
    private int maxRetries;

    @Bean(name = "openRouterRestClient")
    public RestClient openRouterRestClient(RestClient.Builder restClientBuilder) {
        return restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("HTTP-Referer", "http://localhost:8080")
                .defaultHeader("X-Title", "RiskVision AI")
                .build();
    }
}

