package ai.riskvision.graveyard.client;

import ai.riskvision.graveyard.config.OpenRouterConfiguration;
import ai.riskvision.graveyard.dto.ai.OpenRouterRequestDTO;
import ai.riskvision.graveyard.dto.ai.OpenRouterResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OpenRouterClient {

    private final RestClient openRouterRestClient;
    private final OpenRouterConfiguration config;

    /**
     * Sends a chat completion request with system and user prompts.
     */
    public String getCompletion(String systemPrompt, String userPrompt) {
        List<OpenRouterRequestDTO.MessageDTO> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(OpenRouterRequestDTO.MessageDTO.builder()
                    .role("system")
                    .content(systemPrompt)
                    .build());
        }
        if (userPrompt != null && !userPrompt.isBlank()) {
            messages.add(OpenRouterRequestDTO.MessageDTO.builder()
                    .role("user")
                    .content(userPrompt)
                    .build());
        }
        return getCompletionMessages(messages);
    }

    /**
     * Sends a full conversation message history list to OpenRouter.
     */
    public String getCompletionMessages(List<OpenRouterRequestDTO.MessageDTO> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("Messages list cannot be null or empty");
        }

        List<String> modelsToTry = new ArrayList<>();
        if (config.getDefaultModel() != null && !config.getDefaultModel().isBlank()) {
            modelsToTry.add(config.getDefaultModel());
        }
        if (config.getFallbackModels() != null) {
            for (String fallback : config.getFallbackModels()) {
                if (!modelsToTry.contains(fallback)) {
                    modelsToTry.add(fallback);
                }
            }
        }

        if (modelsToTry.isEmpty()) {
            modelsToTry.add("openrouter/free");
        }

        Exception lastException = null;

        for (String model : modelsToTry) {
            log.info("[OpenRouterClient] OpenRouter request started. Target model: {}", model);
            int retryCount = 0;
            int maxRetries = Math.max(config.getMaxRetries(), 1);
            long backoffMs = 1000;

            while (retryCount <= maxRetries) {
                try {
                    OpenRouterRequestDTO request = OpenRouterRequestDTO.builder()
                            .model(model)
                            .messages(messages)
                            .build();

                    log.debug("[OpenRouterClient] Posting request to https://openrouter.ai/api/v1/chat/completions (Attempt {})", retryCount + 1);

                    OpenRouterResponseDTO response = openRouterRestClient.post()
                            .uri("/chat/completions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(request)
                            .retrieve()
                            .body(OpenRouterResponseDTO.class);

                    log.info("[OpenRouterClient] OpenRouter status code: 200 OK for model: {}", model);

                    if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                        OpenRouterResponseDTO.ChoiceDTO choice = response.getChoices().get(0);
                        if (choice.getMessage() != null && choice.getMessage().getContent() != null) {
                            String content = choice.getMessage().getContent();
                            log.info("[OpenRouterClient] Response parsing completed successfully. Output length: {} chars", content.length());
                            return content;
                        }
                    }

                    if (response != null && response.getError() != null) {
                        String errMsg = response.getError().getMessage();
                        log.error("[OpenRouterClient] OpenRouter returned error payload: {}", errMsg);
                        throw new RuntimeException("OpenRouter API Error: " + errMsg);
                    }

                    throw new RuntimeException("Empty choice content received from OpenRouter API");

                } catch (RestClientResponseException e) {
                    lastException = e;
                    int statusCode = e.getStatusCode().value();
                    log.warn("[OpenRouterClient] OpenRouter status code: {} for model: {}. Body: {}", 
                            statusCode, model, e.getResponseBodyAsString());

                    if (statusCode == 401) {
                        log.error("[OpenRouterClient] Unauthorized (401): Invalid or revoked OPENROUTER_API_KEY");
                        throw new RuntimeException("Invalid OpenRouter API Key (401 Unauthorized)", e);
                    } else if (statusCode == 403) {
                        log.error("[OpenRouterClient] Forbidden (403): Access denied by OpenRouter");
                        throw new RuntimeException("OpenRouter Access Forbidden (403)", e);
                    } else if (statusCode == 429) {
                        String retryAfterHeader = e.getResponseHeaders() != null ? 
                                e.getResponseHeaders().getFirst("Retry-After") : null;
                        long sleepTime = backoffMs;
                        if (retryAfterHeader != null) {
                            try {
                                sleepTime = Long.parseLong(retryAfterHeader) * 1000;
                            } catch (NumberFormatException nfe) {
                                log.debug("[OpenRouterClient] Invalid Retry-After header format, using backoff.");
                            }
                        }
                        log.warn("[OpenRouterClient] Rate limited (429). Retrying after {} ms...", sleepTime);
                        sleep(sleepTime);
                        retryCount++;
                        backoffMs *= 2;
                    } else if (statusCode >= 500) {
                        log.warn("[OpenRouterClient] Server error ({}). Retrying after {} ms...", statusCode, backoffMs);
                        sleep(backoffMs);
                        retryCount++;
                        backoffMs *= 2;
                    } else {
                        log.error("[OpenRouterClient] Client error ({}). Skipping retries for model {}", statusCode, model);
                        break;
                    }
                } catch (Exception e) {
                    lastException = e;
                    log.warn("[OpenRouterClient] Network or unexpected error calling OpenRouter model {} (retry {}/{}): {}", 
                            model, retryCount, maxRetries, e.getMessage());
                    sleep(backoffMs);
                    retryCount++;
                    backoffMs *= 2;
                }
            }
            log.warn("[OpenRouterClient] Model {} failed or exhausted retries. Trying next model...", model);
        }

        log.error("[OpenRouterClient] All OpenRouter models failed. Last exception: {}", 
                lastException != null ? lastException.getMessage() : "Unknown error");
        throw new RuntimeException("AI Copilot is temporarily unavailable. All OpenRouter models failed.", lastException);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted during retry sleep", e);
        }
    }
}
