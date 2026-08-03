package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardService service;

    @GetMapping("/system-status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        log.debug("HTTP GET /api/v1/dashboard/system-status requested");
        return ResponseEntity.ok(service.getSystemStatus());
    }

    @GetMapping({"", "/overview"})
    public ResponseEntity<Map<String, Object>> getOverview() {
        log.debug("HTTP GET /api/v1/dashboard/overview requested");
        return ResponseEntity.ok(service.getOverview());
    }

    @GetMapping("/graveyard-index")
    public ResponseEntity<Map<String, Object>> getGraveyardIndex() {
        log.debug("HTTP GET /api/v1/dashboard/graveyard-index requested");
        return ResponseEntity.ok(service.getGraveyardIndex());
    }

    @GetMapping("/org-health")
    public ResponseEntity<Map<String, Object>> getOrgHealth() {
        log.debug("HTTP GET /api/v1/dashboard/org-health requested");
        return ResponseEntity.ok(service.getOrgHealth());
    }

    @GetMapping("/risk-distribution")
    public ResponseEntity<Map<String, Object>> getRiskDistribution() {
        log.debug("HTTP GET /api/v1/dashboard/risk-distribution requested");
        return ResponseEntity.ok(service.getRiskDistribution());
    }

    @GetMapping("/project-lifecycle")
    public ResponseEntity<Map<String, Object>> getProjectLifecycle() {
        log.debug("HTTP GET /api/v1/dashboard/project-lifecycle requested");
        return ResponseEntity.ok(service.getProjectLifecycleCounts());
    }

    @GetMapping("/risk-heatmap")
    public ResponseEntity<Map<String, Object>> getRiskHeatmap(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "risk_level", required = false) String riskLevel,
            @RequestParam(value = "sort_by", required = false) String sortBy,
            @RequestParam(value = "sort_desc", required = false) Boolean sortDesc,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "10") int pageSize) {
        log.debug("HTTP GET /api/v1/dashboard/risk-heatmap requested");
        return ResponseEntity.ok(service.getRiskHeatmap(search, riskLevel, sortBy, sortDesc, page, pageSize));
    }

    @GetMapping("/prediction-summary")
    public ResponseEntity<Map<String, Object>> getPredictionSummary() {
        log.debug("HTTP GET /api/v1/dashboard/prediction-summary requested");
        return ResponseEntity.ok(service.getPredictionSummary());
    }

    @GetMapping("/repository-ranking")
    public ResponseEntity<Map<String, Object>> getRepositoryRanking(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "risk_level", required = false) String riskLevel,
            @RequestParam(value = "sort_by", required = false) String sortBy,
            @RequestParam(value = "sort_desc", required = false) Boolean sortDesc,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "10") int pageSize) {
        log.debug("HTTP GET /api/v1/dashboard/repository-ranking requested search={}, risk_level={}, sort_by={}", search, riskLevel, sortBy);
        return ResponseEntity.ok(service.getRepositoryRanking(search, riskLevel, sortBy, sortDesc, page, pageSize));
    }

    @GetMapping({"/high-risk", "/high-risk-projects"})
    public ResponseEntity<Map<String, Object>> getHighRiskProjects(
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        log.debug("HTTP GET /api/v1/dashboard/high-risk requested limit={}", limit);
        return ResponseEntity.ok(service.getHighRiskProjects(limit));
    }

    @GetMapping("/feature-importance")
    public ResponseEntity<Map<String, Object>> getFeatureImportance() {
        log.debug("HTTP GET /api/v1/dashboard/feature-importance requested");
        return ResponseEntity.ok(service.getFeatureImportance());
    }

    @GetMapping("/prediction-timeline")
    public ResponseEntity<Map<String, Object>> getPredictionTimeline(
            @RequestParam(value = "granularity", defaultValue = "daily") String granularity) {
        log.debug("HTTP GET /api/v1/dashboard/prediction-timeline requested granularity={}", granularity);
        return ResponseEntity.ok(service.getPredictionTimeline(granularity));
    }

    @GetMapping("/recommendations")
    public ResponseEntity<Map<String, Object>> getRecommendations() {
        log.debug("HTTP GET /api/v1/dashboard/recommendations requested");
        return ResponseEntity.ok(service.getRecommendations());
    }

    @GetMapping("/alerts")
    public ResponseEntity<Map<String, Object>> getAlerts() {
        log.debug("HTTP GET /api/v1/dashboard/alerts requested");
        return ResponseEntity.ok(service.getAlerts());
    }

    @GetMapping("/model-info")
    public ResponseEntity<Map<String, Object>> getModelInfo() {
        log.debug("HTTP GET /api/v1/dashboard/model-info requested");
        return ResponseEntity.ok(service.getModelInfo());
    }

    @GetMapping("/activity")
    public ResponseEntity<Map<String, Object>> getActivity(
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        log.debug("HTTP GET /api/v1/dashboard/activity requested limit={}", limit);
        return ResponseEntity.ok(service.getActivity(limit));
    }

    @GetMapping("/forecast")
    public ResponseEntity<Map<String, Object>> getForecast() {
        log.debug("HTTP GET /api/v1/dashboard/forecast requested");
        return ResponseEntity.ok(service.getForecast());
    }

    @GetMapping("/executive-summary")
    public ResponseEntity<Map<String, Object>> getExecutiveSummary() {
        log.debug("HTTP GET /api/v1/dashboard/executive-summary requested");
        return ResponseEntity.ok(service.getExecutiveSummary());
    }

    @GetMapping("/ai-insights")
    public ResponseEntity<Map<String, Object>> getAIInsights(
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        log.debug("HTTP GET /api/v1/dashboard/ai-insights requested limit={}", limit);
        return ResponseEntity.ok(service.getAIInsights(limit));
    }

    @PostMapping("/export")
    public ResponseEntity<Map<String, Object>> exportReport(
            @RequestBody Map<String, Object> payload) {
        String format = (String) payload.getOrDefault("format", "csv");
        String reportType = (String) payload.getOrDefault("report_type", "executive");
        String from = (String) payload.get("date_from");
        String to = (String) payload.get("date_to");
        log.info("HTTP POST /api/v1/dashboard/export requested format={}, report_type={}", format, reportType);
        return ResponseEntity.ok(service.exportReport(format, reportType, from, to));
    }
}
