package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.dto.ai.ChatRequestDTO;
import ai.riskvision.graveyard.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;



import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class AIController {

    private final TelemetrySummaryService telemetrySummaryService;
    private final RiskExplanationService riskExplanationService;
    private final AuditAnalysisService auditAnalysisService;
    private final RepositoryAnalysisService repositoryAnalysisService;
    private final OpenRouterService openRouterService;

    // ─── Telemetry Analysis ──────────────────────────────────────────────────
    @GetMapping("/telemetry-analysis")
    public ResponseEntity<String> getTelemetryAnalysis() {
        return ResponseEntity.ok(telemetrySummaryService.analyzeTelemetry());
    }

    @GetMapping(value = "/telemetry-analysis/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTelemetryAnalysis() {
        return telemetrySummaryService.streamAnalyzeTelemetry();
    }

    // ─── Repository Risk Analysis ─────────────────────────────────────────────
    @GetMapping("/repository/{repoId}/risk-analysis")
    public ResponseEntity<String> getRepositoryRiskAnalysis(@PathVariable UUID repoId) {
        return ResponseEntity.ok(repositoryAnalysisService.analyzeRepository(repoId));
    }

    @GetMapping(value = "/repository/{repoId}/risk-analysis/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRepositoryRiskAnalysis(@PathVariable UUID repoId) {
        return repositoryAnalysisService.streamAnalyzeRepository(repoId);
    }

    // ─── Security Summary ─────────────────────────────────────────────────────
    @GetMapping("/repository/{repoId}/security-summary")
    public ResponseEntity<String> getSecuritySummary(@PathVariable UUID repoId) {
        return ResponseEntity.ok(repositoryAnalysisService.generateSecuritySummary(repoId));
    }

    @GetMapping(value = "/repository/{repoId}/security-summary/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSecuritySummary(@PathVariable UUID repoId) {
        return repositoryAnalysisService.streamGenerateSecuritySummary(repoId);
    }

    // ─── GitHub Repository Insights ───────────────────────────────────────────
    @GetMapping("/repository/{repoId}/github-insights")
    public ResponseEntity<String> getGithubInsights(@PathVariable UUID repoId) {
        return ResponseEntity.ok(repositoryAnalysisService.getGithubInsights(repoId));
    }

    @GetMapping(value = "/repository/{repoId}/github-insights/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamGithubInsights(@PathVariable UUID repoId) {
        return repositoryAnalysisService.streamGetGithubInsights(repoId);
    }

    // ─── Code Risk Explanation ───────────────────────────────────────────────
    @GetMapping("/repository/{repoId}/code-risk")
    public ResponseEntity<String> getCodeRiskExplanation(@PathVariable UUID repoId) {
        return ResponseEntity.ok(repositoryAnalysisService.explainCodeRisk(repoId));
    }

    @GetMapping(value = "/repository/{repoId}/code-risk/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamCodeRiskExplanation(@PathVariable UUID repoId) {
        return repositoryAnalysisService.streamExplainCodeRisk(repoId);
    }

    // ─── Recommendations ──────────────────────────────────────────────────────
    @GetMapping("/repository/{repoId}/recommendations")
    public ResponseEntity<String> getRecommendations(@PathVariable UUID repoId) {
        return ResponseEntity.ok(repositoryAnalysisService.generateRecommendations(repoId));
    }

    @GetMapping(value = "/repository/{repoId}/recommendations/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRecommendations(@PathVariable UUID repoId) {
        return repositoryAnalysisService.streamGenerateRecommendations(repoId);
    }

    // ─── Threat Explanation ──────────────────────────────────────────────────
    @GetMapping("/repository/{repoId}/threat")
    public ResponseEntity<String> getThreatExplanation(@PathVariable UUID repoId) {
        return ResponseEntity.ok(riskExplanationService.explainThreat(repoId));
    }

    @GetMapping(value = "/repository/{repoId}/threat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamThreatExplanation(@PathVariable UUID repoId) {
        return riskExplanationService.streamExplainThreat(repoId);
    }

    // ─── Prediction Explanation ───────────────────────────────────────────────
    @GetMapping("/explain-prediction/{repoId}")
    public ResponseEntity<String> explainPrediction(@PathVariable UUID repoId) {
        return ResponseEntity.ok(riskExplanationService.explainPrediction(repoId));
    }

    @GetMapping(value = "/explain-prediction/{repoId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamExplainPrediction(@PathVariable UUID repoId) {
        return riskExplanationService.streamExplainPrediction(repoId);
    }

    // ─── Model Confidence Explanation ──────────────────────────────────────────
    @GetMapping("/model-confidence/{repoId}")
    public ResponseEntity<String> explainModelConfidence(@PathVariable UUID repoId) {
        return ResponseEntity.ok(riskExplanationService.explainModelConfidence(repoId));
    }

    @GetMapping(value = "/model-confidence/{repoId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamExplainModelConfidence(@PathVariable UUID repoId) {
        return riskExplanationService.streamExplainModelConfidence(repoId);
    }

    // ─── Event Log Explanation ────────────────────────────────────────────────
    @PostMapping("/explain-event")
    public ResponseEntity<String> explainEvent(@RequestBody Map<String, Object> eventData) {
        return ResponseEntity.ok(auditAnalysisService.explainEvent(eventData));
    }

    @PostMapping(value = "/explain-event/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamExplainEvent(@RequestBody Map<String, Object> eventData) {
        return auditAnalysisService.streamExplainEvent(eventData);
    }

    // ─── System Audit Explanation ─────────────────────────────────────────────
    @GetMapping("/system-audit")
    public ResponseEntity<String> explainSystemAudits() {
        return ResponseEntity.ok(auditAnalysisService.explainSystemAudits());
    }

    @GetMapping(value = "/system-audit/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamExplainSystemAudits() {
        return auditAnalysisService.streamExplainSystemAudits();
    }

    // ─── Deployment Failure Explanation ───────────────────────────────────────
    @GetMapping("/repository/{repoId}/deployment-failure")
    public ResponseEntity<String> explainDeploymentFailure(@PathVariable UUID repoId) {
        return ResponseEntity.ok(auditAnalysisService.explainDeploymentFailure(repoId));
    }

    @GetMapping(value = "/repository/{repoId}/deployment-failure/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamExplainDeploymentFailure(@PathVariable UUID repoId) {
        return auditAnalysisService.streamExplainDeploymentFailure(repoId);
    }

    // ─── Incident Report ─────────────────────────────────────────────────────
    @GetMapping("/repository/{repoId}/incident-report")
    public ResponseEntity<String> generateIncidentReport(@PathVariable UUID repoId) {
        return ResponseEntity.ok(auditAnalysisService.generateIncidentReport(repoId));
    }

    @GetMapping(value = "/repository/{repoId}/incident-report/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamGenerateIncidentReport(@PathVariable UUID repoId) {
        return auditAnalysisService.streamGenerateIncidentReport(repoId);
    }

    // ─── Executive Summary ───────────────────────────────────────────────────
    @GetMapping("/executive-summary")
    public ResponseEntity<String> getExecutiveSummary() {
        return ResponseEntity.ok(telemetrySummaryService.generateExecutiveSummary());
    }

    @GetMapping(value = "/executive-summary/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamExecutiveSummary() {
        return telemetrySummaryService.streamGenerateExecutiveSummary();
    }

    // ─── Weekly Summary ───────────────────────────────────────────────────────
    @GetMapping("/weekly-summary")
    public ResponseEntity<String> getWeeklySummary() {
        return ResponseEntity.ok(telemetrySummaryService.generateWeeklySummary());
    }

    @GetMapping(value = "/weekly-summary/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamWeeklySummary() {
        return telemetrySummaryService.streamGenerateWeeklySummary();
    }

    // ─── AI Copilot Chat Endpoint ─────────────────────────────────────────────
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody ChatRequestDTO chatRequest) {
        long startTime = System.currentTimeMillis();
        String userPrompt = chatRequest != null ? chatRequest.getEffectiveMessage() : "";
        log.info("[AIController] Received AI Copilot chat prompt: '{}' (history size: {})",
                userPrompt.length() > 60 ? userPrompt.substring(0, 57) + "..." : userPrompt,
                (chatRequest != null && chatRequest.getHistory() != null) ? chatRequest.getHistory().size() : 0);
        try {
            log.info("[AIController] Dispatching request to provider: OpenRouter");
            String reply = openRouterService.chat(chatRequest);
            long duration = System.currentTimeMillis() - startTime;
            log.info("[AIController] AI Copilot completion succeeded in {} ms (output length: {} chars)", duration, reply.length());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "reply", reply,
                    "content", reply,
                    "provider", "OpenRouter",
                    "durationMs", duration,
                    "timestamp", System.currentTimeMillis()
            ));
        } catch (IllegalArgumentException e) {
            log.warn("[AIController] Invalid chat request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[AIController] Exception during AI Copilot chat processing after {} ms", duration, e);
            String errMsg = e.getMessage() != null ? e.getMessage() : "Failed to process chat request";
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", errMsg,
                    "message", "AI Copilot is temporarily unavailable: " + errMsg
            ));
        }
    }

    // ─── Cache Control ────────────────────────────────────────────────────────
    @PostMapping("/cache/clear")
    public ResponseEntity<Map<String, Object>> clearCache() {
        openRouterService.clearCache();
        return ResponseEntity.ok(Map.of("success", true, "message", "AI Response Cache cleared successfully"));
    }
}

