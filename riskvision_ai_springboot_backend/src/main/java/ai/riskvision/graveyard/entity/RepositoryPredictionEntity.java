package ai.riskvision.graveyard.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "repository_predictions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepositoryPredictionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Column(name = "failure_probability", nullable = false)
    private Double failureProbability;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "risk_level", length = 50)
    private String riskLevel;

    @Column(name = "confidence", nullable = false)
    private Double confidence;

    @Column(name = "health_score")
    private Double healthScore;

    @Column(name = "model_version", length = 100)
    private String modelVersion;

    @Column(name = "prediction_status", length = 50)
    @Builder.Default
    private String predictionStatus = "COMPLETED";

    @Column(name = "feature_importance_json", columnDefinition = "TEXT")
    private String featureImportanceJson;

    @Column(name = "recommendations_json", columnDefinition = "TEXT")
    private String recommendationsJson;

    @Column(name = "triggered_by", length = 200)
    @Builder.Default
    private String triggeredBy = "MANUAL";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
