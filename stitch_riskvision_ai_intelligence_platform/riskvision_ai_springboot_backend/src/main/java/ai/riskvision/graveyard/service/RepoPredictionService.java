package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.entity.*;
import ai.riskvision.graveyard.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepoPredictionService {

    private final RepositoryEntityRepository repoRepository;
    private final RepositoryPredictionEntityRepository predictionRepository;
    private final RepositoryMetricsEntityRepository metricsRepository;
    private final RepositorySyncService syncService;
    private final ObjectMapper objectMapper;
    private final OpenRouterService openRouterService;

    @Value("${ml.service.url:http://localhost:8000}")
    private String mlServiceUrl;

    @Value("${llm.service.url:http://localhost:5001}")
    private String llmServiceUrl;

    private final RestTemplate restTemplate;

    /**
     * Runs an AI prediction for the given repository.
     * Computes failure probability by calling the Python FastAPI ML Service.
     * Requires live ML Service response — no silent heuristic fallback.
     */
    @Transactional
    public RepositoryPredictionEntity runPrediction(UUID repositoryId, String actor) {
        if (repositoryId == null) {
            throw new IllegalArgumentException("Repository ID must not be null");
        }
        log.info("[RepoPredictionService] Starting prediction for repositoryId={} actor={}", repositoryId, actor);

        RepositoryEntity entity = repoRepository.findById(repositoryId)
                .orElseThrow(() -> {
                    log.warn("[RepoPredictionService] Repository not found in database: {}", repositoryId);
                    return new NoSuchElementException("Repository not found: " + repositoryId);
                });

        log.debug("[RepoPredictionService] Repository loaded: name={} status={} lifecycleStage={}",
                entity.getRepositoryName(), entity.getStatus(), entity.getLifecycleStage());

        // Ensure live metrics and metadata are fresh before generating prediction
        try {
            syncService.syncRepository(repositoryId, actor);
            entity = repoRepository.findById(repositoryId).orElse(entity);
        } catch (Exception ex) {
            log.warn("[RepoPredictionService] Repository sync prior to prediction failed — using existing entity data. Error: {}", ex.getMessage());
        }

        double failureProb = 0.0;
        double confidence = 0.85;
        String riskLevel = "LOW";
        String featureJson = null;
        String recommendationsJson = null;
        String modelVersion = "xgboost-v1.0";

        ai.riskvision.graveyard.entity.RepositoryMetricsEntity metrics = metricsRepository.findByRepositoryId(repositoryId).orElse(null);

        double openIssues = entity.getOpenIssues() != null ? (double) entity.getOpenIssues() : (metrics != null && metrics.getOpenIssues() != null ? (double) metrics.getOpenIssues() : 0.0);
        double contributors = entity.getContributors() != null && entity.getContributors() > 0 ? (double) entity.getContributors() : (metrics != null && metrics.getContributors() != null ? (double) metrics.getContributors() : 1.0);
        double activeDevs = metrics != null && metrics.getActiveContributors() != null && metrics.getActiveContributors() > 0 ? (double) metrics.getActiveContributors() : Math.max(1.0, contributors * 0.6);
        double commits = metrics != null && metrics.getCommitCount() != null ? (double) metrics.getCommitCount() : 50.0;
        double prs = metrics != null && metrics.getPullRequests() != null ? (double) metrics.getPullRequests() : 10.0;
        double mergedPrs = metrics != null && metrics.getMergedPullRequests() != null ? (double) metrics.getMergedPullRequests() : Math.max(1.0, prs * 0.7);
        double failedPrs = metrics != null && metrics.getFailedPullRequests() != null ? (double) metrics.getFailedPullRequests() : Math.max(0.0, prs * 0.1);
        double inactiveDays = metrics != null && metrics.getInactiveDays() != null ? (double) metrics.getInactiveDays() : 5.0;
        double buildSuccess = metrics != null && metrics.getBuildSuccessRate() != null ? metrics.getBuildSuccessRate() : 90.0;

        try {
            String url = mlServiceUrl + "/api/v1/pipeline/predict";
            log.info("[RepoPredictionService] Calling FastAPI ML service — url={} repositoryId={} repoName={}", url, repositoryId, entity.getRepositoryName());

            // Compute unique repository-specific feature vector
            double budget = 50000.0 + Math.abs(entity.getRepositoryName().hashCode() % 450000) + (commits * 500.0);
            double teamSize = activeDevs;
            double actualCost = budget * (0.8 + (inactiveDays > 30 ? 0.4 : 0.1) + (openIssues > 20 ? 0.25 : 0.0));
            double timelineMonths = Math.max(3.0, (commits / 20.0) + (prs / 5.0));
            double actualDuration = timelineMonths * (buildSuccess < 75.0 ? 1.4 : 0.9);
            String statusStr = (entity.getStatus() != null) ? entity.getStatus().toLowerCase() : "active";
            double totalRequirements = Math.max(10.0, prs * 2.0 + openIssues * 0.5 + 5.0);
            double featuresDelivered = Math.max(1.0, mergedPrs * 1.8 + 2.0);
            double requirementsChanged = Math.max(0.0, failedPrs * 2.0 + (openIssues > 10 ? 5.0 : 1.0));
            double identifiedRisks = openIssues;
            double totalTasks = Math.max(20.0, commits * 1.2 + prs * 3.0 + openIssues);

            Map<String, Object> request = new HashMap<>();
            request.put("project_id", repositoryId.toString());
            request.put("project_name", entity.getRepositoryName());
            request.put("budget", budget);
            request.put("actual_cost", actualCost);
            request.put("timeline_months", timelineMonths);
            request.put("actual_duration", actualDuration);
            request.put("team_size", teamSize);
            request.put("status", statusStr);
            request.put("requirements_changed", requirementsChanged);
            request.put("total_requirements", totalRequirements);
            request.put("features_delivered", featuresDelivered);
            request.put("identified_risks", identifiedRisks);
            request.put("total_tasks", totalTasks);

            // Add missing model features
            request.put("open_issues", openIssues);
            request.put("critical_bugs", (double) (metrics != null ? Math.max(0.0, openIssues * 0.1) : 0.0));
            request.put("code_coverage", metrics != null && metrics.getCodeCoverage() != null ? metrics.getCodeCoverage() : 75.0);
            request.put("technical_debt", metrics != null && metrics.getTechnicalDebt() != null ? metrics.getTechnicalDebt() : 0.0);
            request.put("security_vulnerabilities", identifiedRisks);
            request.put("dependency_vulnerabilities", (double) (metrics != null ? 3.0 : 0.0));
            request.put("repository_health", metrics != null && metrics.getDocumentationScore() != null ? metrics.getDocumentationScore() : 80.0);
            request.put("build_failures", (double) (metrics != null && metrics.getFailedPullRequests() != null ? metrics.getFailedPullRequests() : 0.0));
            request.put("deployment_failures", (double) (metrics != null && metrics.getFailedPullRequests() != null ? Math.max(0.0, metrics.getFailedPullRequests() * 0.2) : 0.0));
            request.put("requirement_changes", requirementsChanged);
            request.put("customer_satisfaction", 4.0);
            request.put("priority", entity.getRiskLevel() != null ? entity.getRiskLevel() : "MEDIUM");
            request.put("department", "Engineering");
            request.put("project_type", entity.getProjectType() != null ? entity.getProjectType() : "Web");
            request.put("developer_experience", 5.0);

            log.info("[RepoPredictionService] Feature Vector: project_id={} budget={} cost={} timeline={} duration={} risks={} tasks={}",
                    repositoryId, budget, actualCost, timelineMonths, actualDuration, identifiedRisks, totalTasks);

            ResponseEntity<Map<String, Object>> responseEntity = restTemplate.exchange(
                    url,
                    Objects.requireNonNull(HttpMethod.POST),
                    new HttpEntity<>(request),
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            Map<String, Object> response = (responseEntity != null) ? responseEntity.getBody() : null;
            if (response != null) {
                if (response.containsKey("failure_probability") && response.get("failure_probability") != null) {
                    failureProb = ((Number) response.get("failure_probability")).doubleValue();
                }
                if (response.containsKey("confidence_level") && response.get("confidence_level") != null) {
                    confidence = ((Number) response.get("confidence_level")).doubleValue();
                } else if (response.containsKey("confidence") && response.get("confidence") != null) {
                    confidence = ((Number) response.get("confidence")).doubleValue() / 100.0;
                }
                if (response.containsKey("risk_category") && response.get("risk_category") != null) {
                    riskLevel = (String) response.get("risk_category");
                } else if (response.containsKey("prediction_label") && response.get("prediction_label") != null) {
                    riskLevel = (String) response.get("prediction_label");
                }

                Object factorsObj = response.get("top_risk_factors");
                if (factorsObj instanceof java.util.List<?> list) {
                    java.util.List<Map<String, Object>> normalizedFactors = new java.util.ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> map) {
                            Map<String, Object> copy = new java.util.HashMap<>();
                            map.forEach((k, v) -> copy.put(String.valueOf(k), v));
                            if (!copy.containsKey("feature") && copy.containsKey("feature_name")) {
                                copy.put("feature", copy.get("feature_name"));
                            }
                            if (!copy.containsKey("feature_name") && copy.containsKey("feature")) {
                                copy.put("feature_name", copy.get("feature"));
                            }
                            normalizedFactors.add(copy);
                        }
                    }
                    featureJson = objectMapper.writeValueAsString(normalizedFactors);
                } else if (factorsObj != null) {
                    featureJson = objectMapper.writeValueAsString(factorsObj);
                }

                if (response.containsKey("model_version") && response.get("model_version") != null) {
                    modelVersion = String.valueOf(response.get("model_version"));
                } else {
                    modelVersion = "xgboost-v1.0";
                }

                log.info("[RepoPredictionService] ML Prediction succeeded — repositoryId={} failureProbability={} riskLevel={} modelVersion={}",
                        repositoryId, failureProb, riskLevel, modelVersion);
            } else {
                throw new IllegalStateException("Empty response body received from FastAPI ML Service at: " + url);
            }
        } catch (org.springframework.web.client.HttpStatusCodeException ex) {
            log.error("[RepoPredictionService] FastAPI ML Service returned error status={} body={} error={}",
                    ex.getStatusCode(), ex.getResponseBodyAsString(), ex.getMessage(), ex);
            entity.setPredictionStatus("FAILED");
            repoRepository.save(entity);
            throw new IllegalStateException("Prediction model unavailable. Real repository data was collected, but prediction could not be completed: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString(), ex);
        } catch (Exception e) {
            log.error("[RepoPredictionService] FastAPI ML Service failure at {} for repositoryId={} — error: {}",
                    mlServiceUrl, repositoryId, e.getMessage(), e);
            entity.setPredictionStatus("FAILED");
            repoRepository.save(entity);
            throw new IllegalStateException("Prediction model unavailable. Real repository data was collected, but prediction could not be completed: " + e.getMessage(), e);
        }

        int riskScore = (int) Math.round(failureProb * 100);
        double healthScore = Math.max(0, 100.0 - (failureProb * 100.0));

        // Generate dynamic recommendations using OpenRouter LLM (or fallback if it fails)
        try {
            recommendationsJson = generateRecommendationsWithAI(entity, metrics, riskScore, riskLevel, failureProb, featureJson);
        } catch (Exception ex) {
            log.warn("[RepoPredictionService] AI recommendation generation failed, executing local fallback generator. Error: {}", ex.getMessage());
            recommendationsJson = generateFallbackRecommendations(entity, metrics, riskScore, riskLevel, failureProb, featureJson);
        }

        // Persist prediction record
        RepositoryPredictionEntity prediction = RepositoryPredictionEntity.builder()
                .repositoryId(repositoryId)
                .failureProbability(failureProb)
                .riskScore(riskScore)
                .riskLevel(riskLevel)
                .confidence(confidence)
                .healthScore(healthScore)
                .modelVersion(modelVersion)
                .predictionStatus("COMPLETED")
                .featureImportanceJson(featureJson)
                .recommendationsJson(recommendationsJson)
                .triggeredBy(actor != null ? actor : "MANUAL")
                .build();

        prediction = predictionRepository.save(prediction);

        // Update repository with latest prediction result
        entity.setFailureProbability(failureProb);
        entity.setHealthScore(healthScore);
        entity.setRiskLevel(riskLevel);
        entity.setAiConfidence(confidence);
        entity.setPredictionStatus("COMPLETED");
        repoRepository.save(entity);

        syncService.logActivity(repositoryId, "AI_PREDICTION_RUN",
                "AI prediction completed — Failure probability: " + String.format("%.1f", failureProb * 100) + "%, Risk: " + riskLevel,
                actor, "PREDICTION", "INFO");

        log.info("Prediction completed for repository {} — risk={}, prob={}", repositoryId, riskLevel, failureProb);
        return prediction;
    }

    /**
     * Calls OpenRouter to generate repository-specific rich AI recommendations.
     */
    public String generateRecommendationsWithAI(
            RepositoryEntity entity, RepositoryMetricsEntity metrics, int riskScore, String riskLevel, double failureProb, String featureJson) {
        log.info("[RepoPredictionService] Requesting OpenRouter AI recommendations for repository: {}", entity.getRepositoryName());

        double openIssues = entity.getOpenIssues() != null ? (double) entity.getOpenIssues() : 0.0;
        double contributors = entity.getContributors() != null && entity.getContributors() > 0 ? (double) entity.getContributors() : 1.0;
        double inactiveDays = metrics != null && metrics.getInactiveDays() != null ? (double) metrics.getInactiveDays() : 5.0;
        double buildSuccess = metrics != null && metrics.getBuildSuccessRate() != null ? metrics.getBuildSuccessRate() : 90.0;
        double codeCoverage = metrics != null && metrics.getCodeCoverage() != null ? metrics.getCodeCoverage() : 75.0;
        double techDebt = metrics != null && metrics.getTechnicalDebt() != null ? metrics.getTechnicalDebt() : 0.0;
        double documentationScore = metrics != null && metrics.getDocumentationScore() != null ? metrics.getDocumentationScore() : 80.0;

        String systemPrompt = "You are the AI Recommendation Engine for RiskVision AI. You must analyze the repository metrics and XGBoost risk prediction results, then generate dynamic, actionable project risk remediation recommendations. " +
                "You must output ONLY a valid JSON object matching the exact requested schema. Do not include markdown blocks like ```json or any other text before/after the JSON. " +
                "Do not invent any repository metrics. If a metric value is not provided in the input, do not mention it.";

        String userPrompt = String.format(
                "Generate a project status improvement plan for the software repository '%s'.\n\n" +
                "Repository Details:\n" +
                "- Primary Language: %s\n" +
                "- Technology Stack: %s\n" +
                "- Visibility: %s\n\n" +
                "Current Metrics:\n" +
                "- Open Issues: %s\n" +
                "- Active Contributors: %s\n" +
                "- Build Success Rate: %s%%\n" +
                "- Code Coverage: %s%%\n" +
                "- Inactive Days: %s\n" +
                "- Technical Debt: %s hours\n" +
                "- Documentation Score: %s/100\n\n" +
                "XGBoost Risk Model Output:\n" +
                "- Overall Risk Score: %d/100\n" +
                "- Risk Level Category: %s\n" +
                "- Failure Probability: %.1f%%\n" +
                "- Top Risk Factors (SHAP feature impact): %s\n\n" +
                "Generate a JSON object matching this schema. Focus on the actual high-impact risk factors from the XGBoost model outputs:\n" +
                "{\n" +
                "  \"recommendations\": [\n" +
                "     {\n" +
                "       \"title\": \"Title of the recommendation, e.g. 🔴 P0 — Resolve Critical Security Issues\",\n" +
                "       \"risk_detected\": \"Risk detected detail, e.g. Low test coverage\",\n" +
                "       \"current_condition\": \"Current condition explanation, e.g. Code coverage is currently 45%.\",\n" +
                "       \"recommended_action\": \"Concrete technical or management action to take\",\n" +
                "       \"why_it_matters\": \"Why this action reduces risk\",\n" +
                "       \"expected_impact\": \"Impact level (Critical, High, Medium, or Low)\",\n" +
                "       \"estimated_risk_reduction\": \"Estimated risk reduction (e.g. -5 to -10 points)\",\n" +
                "       \"implementation_effort\": \"Effort (Low, Medium, or High)\",\n" +
                "       \"suggested_priority\": \"Priority (P0 — Immediate, P1 — High, P2 — Medium, P3 — Low)\"\n" +
                "     }\n" +
                "  ],\n" +
                "  \"roadmap\": {\n" +
                "     \"immediate\": [\"List of actions for 0-7 days\"],\n" +
                "     \"short_term\": [\"List of actions for 1-4 weeks\"],\n" +
                "     \"medium_term\": [\"List of actions for 1-3 months\"]\n" +
                "  },\n" +
                "  \"projected_status\": {\n" +
                "     \"projected_risk_score\": 50,\n" +
                "     \"projected_risk_level\": \"MEDIUM\",\n" +
                "     \"potential_improvement\": \"17 points\"\n" +
                "  }\n" +
                "}",
                entity.getRepositoryName(),
                entity.getLanguage() != null ? entity.getLanguage() : "Data unavailable",
                entity.getTechnology() != null ? entity.getTechnology() : "Data unavailable",
                entity.getVisibility(),
                openIssues,
                contributors,
                buildSuccess,
                codeCoverage,
                inactiveDays,
                techDebt,
                documentationScore,
                riskScore,
                riskLevel,
                failureProb * 100,
                featureJson
        );

        String rawResponse = openRouterService.getChatCompletion(systemPrompt, userPrompt);
        return cleanJson(rawResponse);
    }

    private String cleanJson(String raw) {
        if (raw == null) return null;
        String cleaned = raw.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();
        return cleaned;
    }

    public String generateFallbackRecommendations(
            RepositoryEntity entity, RepositoryMetricsEntity metrics, int riskScore, String riskLevel, double failureProb, String featureJson) {
        try {
            double openIssues = entity.getOpenIssues() != null ? (double) entity.getOpenIssues() : 0.0;
            double buildSuccess = metrics != null && metrics.getBuildSuccessRate() != null ? metrics.getBuildSuccessRate() : 85.0;
            double codeCoverage = metrics != null && metrics.getCodeCoverage() != null ? metrics.getCodeCoverage() : 70.0;
            double inactiveDays = metrics != null && metrics.getInactiveDays() != null ? (double) metrics.getInactiveDays() : 5.0;
            double documentationScore = metrics != null && metrics.getDocumentationScore() != null ? (double) metrics.getDocumentationScore() : 75.0;
            double contributors = entity.getContributors() != null && entity.getContributors() > 0 ? (double) entity.getContributors() : (metrics != null && metrics.getContributors() != null ? (double) metrics.getContributors() : 1.0);
            double busFactor = metrics != null && metrics.getBusFactor() != null ? (double) metrics.getBusFactor() : 1.0;

            List<Map<String, Object>> recs = new ArrayList<>();
            int totalEstimatedReduction = 0;

            // 1. Inactive Days / Low activity
            if (inactiveDays > 14) {
                int reduction = inactiveDays > 45 ? 12 : 7;
                totalEstimatedReduction += reduction;
                Map<String, Object> rec = new LinkedHashMap<>();
                rec.put("title", "🟠 P1 — Resume Regular Development Cadence");
                rec.put("risk_detected", "Low repository activity");
                rec.put("current_condition", "No recent development commits detected for '" + entity.getRepositoryName() + "' in the last " + (int) inactiveDays + " days.");
                rec.put("recommended_action", "Establish a regular weekly commit/release cadence and assign active ownership.");
                rec.put("why_it_matters", "Inactivity for >14 days indicates project stagnation or maintainer abandonment.");
                rec.put("expected_impact", "High");
                rec.put("estimated_risk_reduction", "-" + reduction + " points");
                rec.put("implementation_effort", "Medium");
                rec.put("suggested_priority", "P1 — High");
                recs.add(rec);
            }

            // 2. Open Issues pressure
            if (openIssues > 10) {
                int reduction = openIssues > 25 ? 10 : 5;
                totalEstimatedReduction += reduction;
                Map<String, Object> rec = new LinkedHashMap<>();
                rec.put("title", "🟡 P2 — Address Growing Issue Backlog");
                rec.put("risk_detected", "High unresolved issue count");
                rec.put("current_condition", (int) openIssues + " unresolved issues currently open for " + entity.getRepositoryName() + ".");
                rec.put("recommended_action", "Conduct an issue triaging sprint to categorize, resolve blocker bugs, and close stale tickets.");
                rec.put("why_it_matters", "Accumulated issue backlogs degrade user trust and signal unmaintained technical debt.");
                rec.put("expected_impact", "Medium");
                rec.put("estimated_risk_reduction", "-" + reduction + " points");
                rec.put("implementation_effort", "Low");
                rec.put("suggested_priority", "P2 — Medium");
                recs.add(rec);
            }

            // 3. Code Coverage & Testing
            if (codeCoverage < 60.0) {
                int reduction = codeCoverage < 35.0 ? 14 : 8;
                totalEstimatedReduction += reduction;
                Map<String, Object> rec = new LinkedHashMap<>();
                rec.put("title", "🟠 P1 — Implement Automated Test Suite");
                rec.put("risk_detected", "Insufficient automated testing coverage");
                rec.put("current_condition", "Code coverage is estimated at " + String.format("%.1f", codeCoverage) + "%, below the 60.0% standard threshold.");
                rec.put("recommended_action", "Add automated unit and integration tests covering critical business logic pathways.");
                rec.put("why_it_matters", "Low test coverage causes regression risks during updates and slows down code review validation.");
                rec.put("expected_impact", "High");
                rec.put("estimated_risk_reduction", "-" + reduction + " points");
                rec.put("implementation_effort", "High");
                rec.put("suggested_priority", "P1 — High");
                recs.add(rec);
            }

            // 4. Build Success Rate & CI/CD
            if (buildSuccess < 80.0) {
                int reduction = buildSuccess < 65.0 ? 15 : 9;
                totalEstimatedReduction += reduction;
                Map<String, Object> rec = new LinkedHashMap<>();
                rec.put("title", "🔴 P0 — Stabilize CI/CD Build Pipeline");
                rec.put("risk_detected", "Elevated build failure rate");
                rec.put("current_condition", "Build success rate is currently at " + String.format("%.1f", buildSuccess) + "%.");
                rec.put("recommended_action", "Fix failing build scripts and enforce required status checks prior to PR merge.");
                rec.put("why_it_matters", "Build failures interrupt deployment pipelines and hide runtime defects.");
                rec.put("expected_impact", "Critical");
                rec.put("estimated_risk_reduction", "-" + reduction + " points");
                rec.put("implementation_effort", "Medium");
                rec.put("suggested_priority", "P0 — Immediate");
                recs.add(rec);
            }

            // 5. Documentation Score
            if (documentationScore < 60.0) {
                int reduction = 6;
                totalEstimatedReduction += reduction;
                Map<String, Object> rec = new LinkedHashMap<>();
                rec.put("title", "🟡 P2 — Improve Repository Documentation");
                rec.put("risk_detected", "Low documentation coverage");
                rec.put("current_condition", "Documentation score for '" + entity.getRepositoryName() + "' is at " + String.format("%.1f", documentationScore) + "/100.");
                rec.put("recommended_action", "Create comprehensive README.md setup guides, API specifications, and contribution workflows.");
                rec.put("why_it_matters", "Missing documentation increases onboarding friction and causes maintainer knowledge silos.");
                rec.put("expected_impact", "Medium");
                rec.put("estimated_risk_reduction", "-" + reduction + " points");
                rec.put("implementation_effort", "Low");
                rec.put("suggested_priority", "P2 — Medium");
                recs.add(rec);
            }

            // 6. Contributor Concentration / Bus Factor
            if (contributors <= 1 || busFactor <= 1) {
                int reduction = 8;
                totalEstimatedReduction += reduction;
                Map<String, Object> rec = new LinkedHashMap<>();
                rec.put("title", "🟠 P1 — Mitigate Single-Maintainer Risk");
                rec.put("risk_detected", "Single contributor concentration (Bus Factor = 1)");
                rec.put("current_condition", "Repository relies on " + (int) contributors + " contributor, creating single-point-of-failure risk.");
                rec.put("recommended_action", "Onboard co-maintainers, define ownership guidelines, and share domain knowledge.");
                rec.put("why_it_matters", "Single-maintainer projects have high abandonment probability if primary developer turns inactive.");
                rec.put("expected_impact", "High");
                rec.put("estimated_risk_reduction", "-" + reduction + " points");
                rec.put("implementation_effort", "Medium");
                rec.put("suggested_priority", "P1 — High");
                recs.add(rec);
            }

            // If no specific risk threshold breached, add nominal maintainer action
            if (recs.isEmpty()) {
                totalEstimatedReduction = 3;
                Map<String, Object> rec = new LinkedHashMap<>();
                rec.put("title", "🟢 P3 — Maintain Engineering Standards");
                rec.put("risk_detected", "No critical risk factors detected");
                rec.put("current_condition", "Repository metrics for '" + entity.getRepositoryName() + "' are within healthy operational thresholds.");
                rec.put("recommended_action", "Maintain current commit frequency, run periodic dependency security scans, and monitor PR velocity.");
                rec.put("why_it_matters", "Proactive maintenance preserves low failure probability over the project lifecycle.");
                rec.put("expected_impact", "Low");
                rec.put("estimated_risk_reduction", "-3 points");
                rec.put("implementation_effort", "Low");
                rec.put("suggested_priority", "P3 — Low");
                recs.add(rec);
            }

            // Generate dynamic 3-phase roadmap derived from recommendations
            List<String> immediate = new ArrayList<>();
            List<String> shortTerm = new ArrayList<>();
            List<String> mediumTerm = new ArrayList<>();

            for (Map<String, Object> r : recs) {
                String priority = (String) r.get("suggested_priority");
                String action = (String) r.get("recommended_action");
                if (priority.startsWith("P0")) {
                    immediate.add(action);
                } else if (priority.startsWith("P1")) {
                    shortTerm.add(action);
                } else {
                    mediumTerm.add(action);
                }
            }

            if (immediate.isEmpty()) {
                immediate.add("Audit branch protection rules and verify CI/CD execution status");
            }
            if (shortTerm.isEmpty()) {
                shortTerm.add("Review code review turn-around times and open issue backlog");
            }
            if (mediumTerm.isEmpty()) {
                mediumTerm.add("Schedule monthly RiskVision AI telemetry health audits");
            }

            Map<String, Object> roadmap = new LinkedHashMap<>();
            roadmap.put("immediate", immediate);
            roadmap.put("short_term", shortTerm);
            roadmap.put("medium_term", mediumTerm);

            // Compute dynamic repository-specific projected status
            int projectedRisk = Math.max(5, riskScore - totalEstimatedReduction);
            String projectedLevel = "LOW";
            if (projectedRisk >= 75) projectedLevel = "CRITICAL";
            else if (projectedRisk >= 50) projectedLevel = "HIGH";
            else if (projectedRisk >= 25) projectedLevel = "MEDIUM";

            Map<String, Object> projectedStatus = new LinkedHashMap<>();
            projectedStatus.put("projected_risk_score", projectedRisk);
            projectedStatus.put("projected_risk_level", projectedLevel);
            projectedStatus.put("potential_improvement", (riskScore - projectedRisk) + " points");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("recommendations", recs);
            result.put("roadmap", roadmap);
            result.put("projected_status", projectedStatus);

            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("Failed to generate fallback recommendations JSON: {}", e.getMessage(), e);
            return "{\"recommendations\":[],\"roadmap\":{\"immediate\":[],\"short_term\":[],\"medium_term\":[]},\"projected_status\":{}}";
        }
    }

}
