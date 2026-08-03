package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.client.OpenRouterClient;
import ai.riskvision.graveyard.dto.ai.ChatMessageDTO;
import ai.riskvision.graveyard.dto.ai.ChatRequestDTO;
import ai.riskvision.graveyard.dto.ai.OpenRouterRequestDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenRouterService {

    private static final String DEFAULT_COPILOT_SYSTEM_PROMPT = 
            "You are RiskVision AI Copilot, an expert AI assistant specializing in repository risk analysis, telemetry metrics, vulnerability detection, and software architecture health. Provide concise, professional, clear, and actionable responses.";

    private final OpenRouterClient openRouterClient;
    private final Map<String, CacheEntry> responseCache = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    private static final long TTL_MS = 10 * 60 * 1000; // 10 minutes cache TTL

    /**
     * Handles interactive chat request from the AI Copilot UI with context & history.
     */
    public String chat(ChatRequestDTO chatRequest) {
        String userMessage = chatRequest.getEffectiveMessage();
        if (userMessage.isBlank()) {
            throw new IllegalArgumentException("User chat message cannot be empty");
        }

        List<OpenRouterRequestDTO.MessageDTO> messages = new ArrayList<>();
        // Add System Persona Prompt
        messages.add(OpenRouterRequestDTO.MessageDTO.builder()
                .role("system")
                .content(DEFAULT_COPILOT_SYSTEM_PROMPT)
                .build());

        // Append conversation history
        if (chatRequest.getHistory() != null) {
            for (ChatMessageDTO historyItem : chatRequest.getHistory()) {
                String content = historyItem.getEffectiveContent();
                if (!content.isBlank()) {
                    messages.add(OpenRouterRequestDTO.MessageDTO.builder()
                            .role(historyItem.getEffectiveRole())
                            .content(content)
                            .build());
                }
            }
        }

        // Append latest user message if not already the last item in history
        if (messages.isEmpty() || !messages.get(messages.size() - 1).getContent().equals(userMessage)) {
            messages.add(OpenRouterRequestDTO.MessageDTO.builder()
                    .role("user")
                    .content(userMessage)
                    .build());
        }

        log.info("[OpenRouterService] Executing chat request with {} total messages in context", messages.size());
        return openRouterClient.getCompletionMessages(messages);
    }

    /**
     * Standard synchronous call to get completion. Utilizes caching.
     */
    public String getChatCompletion(String systemPrompt, String userPrompt) {
        String cacheKey = generateCacheKey(systemPrompt, userPrompt);
        CacheEntry cached = responseCache.get(cacheKey);

        if (cached != null && (System.currentTimeMillis() - cached.getTimestamp()) < TTL_MS) {
            log.info("Cache hit for prompt. Reusing cached AI response.");
            return cached.getContent();
        }

        String response = openRouterClient.getCompletion(systemPrompt, userPrompt);
        responseCache.put(cacheKey, new CacheEntry(response, System.currentTimeMillis()));
        return response;
    }

    /**
     * Streams the chat completion using Server-Sent Events (SseEmitter).
     * If cache is hit, it streams the cached response quickly.
     */
    public SseEmitter streamChatCompletion(String systemPrompt, String userPrompt) {
        SseEmitter emitter = new SseEmitter(90000L); // 90 seconds timeout
        String cacheKey = generateCacheKey(systemPrompt, userPrompt);
        CacheEntry cached = responseCache.get(cacheKey);

        executorService.submit(() -> {
            try {
                String fullContent;
                if (cached != null && (System.currentTimeMillis() - cached.getTimestamp()) < TTL_MS) {
                    log.info("Cache hit for streaming prompt.");
                    fullContent = cached.getContent();
                } else {
                    fullContent = openRouterClient.getCompletion(systemPrompt, userPrompt);
                    responseCache.put(cacheKey, new CacheEntry(fullContent, System.currentTimeMillis()));
                }

                // Stream the content character by character or word by word to simulate typing
                String[] chunks = fullContent.split("(?<=\\s)|(?=\\s)");
                for (String chunk : chunks) {
                    emitter.send(SseEmitter.event()
                            .name("chunk")
                            .data(chunk));
                    Thread.sleep(15); // Smooth 15ms typing animation delay
                }
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();
            } catch (Exception e) {
                log.error("Error during SSE AI completion streaming", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (Exception se) {
                    log.error("Failed to send error state over SSE", se);
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * Generates a unique SHA-256 hash as a cache key for the prompt combination.
     */
    private String generateCacheKey(String systemPrompt, String userPrompt) {
        try {
            String combined = systemPrompt + "||" + userPrompt;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Error generating cache key, fallback to simple string hash", e);
            return String.valueOf((systemPrompt + userPrompt).hashCode());
        }
    }

    /**
     * Clears all cached AI responses.
     */
    public void clearCache() {
        responseCache.clear();
        log.info("OpenRouter AI response cache cleared.");
    }

    @Getter
    @AllArgsConstructor
    private static class CacheEntry {
        private final String content;
        private final long timestamp;
    }
}
