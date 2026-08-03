package ai.riskvision.graveyard.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "prediction_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PredictionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "project_id", nullable = false)
    private java.util.UUID projectId;

    @Column(name = "project_name")
    private String projectName;

    @Column(name = "failure_probability", nullable = false)
    private Double failureProbability;

    @Column(name = "risk_score", nullable = false)
    private Integer riskScore;

    @Column(name = "risk_level", nullable = false)
    private String riskLevel;

    @Column(name = "confidence_level", nullable = false)
    private Double confidenceLevel;

    @Column(name = "commits_today")
    private Integer commitsToday;

    @Column(name = "merged_prs")
    private Integer mergedPrs;

    @Column(name = "open_issues")
    private Integer openIssues;

    @Column(name = "closed_issues")
    private Integer closedIssues;

    @Column(name = "failed_builds")
    private Integer failedBuilds;

    @Column(name = "successful_builds")
    private Integer successfulBuilds;

    @Column(name = "predicted_at", nullable = false)
    private LocalDateTime predictedAt;
}
