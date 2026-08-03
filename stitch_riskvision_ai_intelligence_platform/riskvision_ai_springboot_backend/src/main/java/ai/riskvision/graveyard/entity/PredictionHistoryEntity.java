package ai.riskvision.graveyard.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "prediction_history", indexes = {
    @Index(name = "idx_prediction_history_repo_id", columnList = "repository_id"),
    @Index(name = "idx_prediction_history_project_id", columnList = "project_id"),
    @Index(name = "idx_prediction_history_risk_level", columnList = "risk_level"),
    @Index(name = "idx_prediction_history_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionHistoryEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "repository_id", length = 36)
    private String repositoryId;

    @Column(name = "project_id", length = 36)
    private String projectId;

    @Column(name = "risk_score", nullable = false)
    private Double riskScore;

    @Column(name = "risk_level", length = 20, nullable = false)
    private String riskLevel;

    @Column(name = "confidence", nullable = false)
    private Double confidence;

    @Column(name = "probability", nullable = false)
    private Double probability;

    @Column(name = "top_factors", columnDefinition = "TEXT")
    private String topFactors;

    @Column(name = "prediction_json", columnDefinition = "TEXT")
    private String predictionJson;

    @Column(name = "model_version", length = 50, nullable = false)
    private String modelVersion;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;
}
