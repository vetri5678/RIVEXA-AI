package ai.riskvision.graveyard.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AIAnalysisService {

    private final OpenRouterService openRouterService;
    private final PromptTemplateService promptTemplateService;

    /**
     * Executes AI analysis synchronously and returns the parsed JSON string.
     */
    public String analyze(String feature, Map<String, Object> variables) {
        String systemPrompt = promptTemplateService.getSystemPrompt();
        String userPrompt = promptTemplateService.getUserPrompt(feature, variables);
        return openRouterService.getChatCompletion(systemPrompt, userPrompt);
    }

    /**
     * Executes AI analysis and streams the response chunk by chunk over SSE.
     */
    public SseEmitter streamAnalyze(String feature, Map<String, Object> variables) {
        String systemPrompt = promptTemplateService.getSystemPrompt();
        String userPrompt = promptTemplateService.getUserPrompt(feature, variables);
        return openRouterService.streamChatCompletion(systemPrompt, userPrompt);
    }
}
