package ai.riskvision.graveyard.dto.prediction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * PredictionResultResponse — full response for:
 *   POST /api/v1/predictions/run
 *   GET  /api/v1/predictions/{id}
 *
 * <p>Contains the complete prediction result, including repository details,
 * risk metrics, SHAP feature importance JSON, and recommendations JSON.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionResultResponse {

    // ── Prediction Identity ────────────────────────────────────────────────────
    private UUID predictionId;
    private String predictionStatus;
    private String modelVersion;
    private String triggeredBy;
    private LocalDateTime createdAt;

    // ── Repository Info ────────────────────────────────────────────────────────
    private UUID repositoryId;
    private String repositoryName;
    private String repositoryUrl;
    private String organization;
    private String language;
    private String gitProvider;
    private String branch;
    private String visibility;

    // ── Risk Metrics ───────────────────────────────────────────────────────────
    private Double failureProbability;
    private Integer riskScore;
    private String riskLevel;
    private Double confidence;
    private Double healthScore;

    // ── SHAP / Features (raw JSON strings, parsed by the frontend) ─────────────
    private String featureImportanceJson;
    private String recommendationsJson;
}
