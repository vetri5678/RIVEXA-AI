package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardService service;

    @GetMapping("/system-status")
    public ResponseEntity<Map<String, Object>> getSystemStatus(Principal principal) {
        log.debug("HTTP GET /api/v1/dashboard/system-status requested");
        return ResponseEntity.ok(service.getSystemStatus());
    }

    @GetMapping({"", "/overview"})
    public ResponseEntity<Map<String, Object>> getOverview(Principal principal) {
        log.debug("HTTP GET /api/v1/dashboard/overview requested");
        String email = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(service.getOverview(email));
    }

    @GetMapping("/graveyard-index")
    public ResponseEntity<Map<String, Object>> getGraveyardIndex(Principal principal) {
        log.debug("HTTP GET /api/v1/dashboard/graveyard-index requested");
        String email = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(service.getGraveyardIndex(email));
    }

    @GetMapping("/org-health")
    public ResponseEntity<Map<String, Object>> getOrgHealth(Principal principal) {
        log.debug("HTTP GET /api/v1/dashboard/org-health requested");
        String email = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(service.getOrgHealth(email));
    }

    @GetMapping("/risk-distribution")
    public ResponseEntity<Map<String, Object>> getRiskDistribution(Principal principal) {
        log.debug("HTTP GET /api/v1/dashboard/risk-distribution requested");
        String email = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(service.getRiskDistribution(email));
    }

    @GetMapping("/project-lifecycle")
    public ResponseEntity<Map<String, Object>> getProjectLifecycle(Principal principal) {
        log.debug("HTTP GET /api/v1/dashboard/project-lifecycle requested");
        String email = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(service.getProjectLifecycleCounts(email));
    }

    @GetMapping("/risk-heatmap")
    public ResponseEntity<Map<String, Object>> getRiskHeatmap(
            Principal principal,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "risk_level", required = false) String riskLevel,
            @RequestParam(value = "sort_by", required = false) String sortBy,
            @RequestParam(value = "sort_desc", required = false) Boolean sortDesc,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "10") int pageSize) {
        log.debug("HTTP GET /api/v1/dashboard/risk-heatmap requested");
        String email = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(service.getRiskHeatmap(email, search, riskLevel, sortBy, sortDesc, page, pageSize));
    }

    @GetMapping("/prediction-summary")
    public ResponseEntity<Map<String, Object>> getPredictionSummary(Principal principal) {
        log.debug("HTTP GET /api/v1/dashboard/prediction-summary requested");
        String email = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(service.getPredictionSummary(email));
    }

    @GetMapping("/repository-ranking")
    public ResponseEntity<Map<String, Object>> getRepositoryRanking(
            Principal principal,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "risk_level", required = false) String riskLevel,
            @RequestParam(value = "sort_by", required = false) String sortBy,
            @RequestParam(value = "sort_desc", required = false) Boolean sortDesc,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "10") int pageSize) {
        log.debug("HTTP GET /api/v1/dashboard/repository-ranking requested search={}, risk_level={}, sort_by={}", search, riskLevel, sortBy);
        String email = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(service.getRepositoryRanking(email, search, riskLevel, sortBy, sortDesc, page, pageSize));
    }

    @GetMapping({"/high-risk", "/high-risk-projects"})
    public ResponseEntity<Map<String, Object>> getHighRiskProjects(
            Principal principal,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        log.debug("HTTP GET /api/v1/dashboard/high-risk requested limit={}", limit);
        String email = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(service.getHighRiskProjects(email, limit));
    }

    @GetMapping("/feature-importance")
    public ResponseEntity<Map<String, Object>> getFeatureImportance(Principal principal) {
        log.debug("HTTP GET /api/v1/dashboard/feature-importance requested");
        return ResponseEntity.ok(service.getFeatureImportance());
    }

    @GetMapping("/prediction-timeline")
    public ResponseEntity<Map<String, Object>> getPredictionTimeline(
            Principal principal,
            @RequestParam(value = "granularity", defaultValue = "daily") String granularity) {
        log.debug("HTTP GET /api/v1/dashboard/prediction-timeline requested granularity={}", granularity);
        return ResponseEntity.ok(service.getPredictionTimeline(granularity));
    }

    @GetMapping("/recommendations")
    public ResponseEntity<Map<String, Object>> getRecommendations(Principal principal) {
        log.debug("HTTP GET /api/v1/dashboard/recommendations requested");
        String email = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(service.getRecommendations(email));
    }

    @GetMapping("/alerts")
    public ResponseEntity<Map<String, Object>> getAlerts(Principal principal) {
        log.debug("HTTP GET /api/v1/dashboard/alerts requested");
        String email = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(service.getAlerts(email));
    }

    @GetMapping("/model-info")
    public ResponseEntity<Map<String, Object>> getModelInfo(Principal principal) {
        log.debug("HTTP GET /api/v1/dashboard/model-info requested");
        return ResponseEntity.ok(service.getModelInfo());
    }

    @GetMapping("/activity")
    public ResponseEntity<Map<String, Object>> getActivity(
            Principal principal,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        log.debug("HTTP GET /api/v1/dashboard/activity requested limit={}", limit);
        String email = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(service.getActivity(email, limit));
    }

    @GetMapping("/forecast")
    public ResponseEntity<Map<String, Object>> getForecast(Principal principal) {
        log.debug("HTTP GET /api/v1/dashboard/forecast requested");
        String email = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(service.getForecast(email));
    }

    @GetMapping("/executive-summary")
    public ResponseEntity<Map<String, Object>> getExecutiveSummary(Principal principal) {
        log.debug("HTTP GET /api/v1/dashboard/executive-summary requested");
        String email = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(service.getExecutiveSummary(email));
    }

    @GetMapping("/ai-insights")
    public ResponseEntity<Map<String, Object>> getAIInsights(
            Principal principal,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        log.debug("HTTP GET /api/v1/dashboard/ai-insights requested limit={}", limit);
        String email = principal != null ? principal.getName() : null;
        return ResponseEntity.ok(service.getAIInsights(email, limit));
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
