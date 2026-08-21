package ai.riskvision.graveyard.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "repositories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepositoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * The RIVEXA user who owns this repository.
     * All dashboard queries MUST filter by this field to enforce per-user data isolation.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Column(name = "repository_name", nullable = false, length = 200)
    private String repositoryName;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "organization", length = 200)
    private String organization;

    @Column(name = "owner", length = 200)
    private String owner;

    @Column(name = "repository_url", nullable = false, length = 500)
    private String repositoryUrl;

    @Column(name = "git_provider", nullable = false, length = 50)
    private String gitProvider;

    @Column(name = "branch", length = 100)
    @Builder.Default
    private String branch = "main";

    @Column(name = "technology", length = 500)
    private String technology;

    @Column(name = "language", length = 200)
    private String language;

    @Column(name = "project_type", length = 100)
    private String projectType;

    @Column(name = "visibility", length = 50)
    @Builder.Default
    private String visibility = "PRIVATE";

    @Column(name = "license", length = 100)
    private String license;

    @Column(name = "health_score")
    @Builder.Default
    private Double healthScore = 0.0;

    @Column(name = "failure_probability")
    @Builder.Default
    private Double failureProbability = 0.0;

    @Column(name = "prediction_status", length = 50)
    @Builder.Default
    private String predictionStatus = "PENDING";

    @Column(name = "lifecycle_stage", length = 50)
    @Builder.Default
    private String lifecycleStage = "ACTIVE";

    @Column(name = "status", length = 50)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "risk_level", length = 50)
    @Builder.Default
    private String riskLevel = "LOW";

    @Column(name = "ai_confidence")
    @Builder.Default
    private Double aiConfidence = 0.0;

    @Column(name = "last_commit_date")
    private LocalDateTime lastCommitDate;

    @Column(name = "last_sync_date")
    private LocalDateTime lastSyncDate;

    @Column(name = "prediction_frequency", length = 50)
    @Builder.Default
    private String predictionFrequency = "WEEKLY";

    @Column(name = "auto_prediction_enabled")
    @Builder.Default
    private Boolean autoPredictionEnabled = true;

    @Column(name = "notifications_enabled")
    @Builder.Default
    private Boolean notificationsEnabled = true;

    @Column(name = "background_sync_enabled")
    @Builder.Default
    private Boolean backgroundSyncEnabled = true;

    @Column(name = "report_generation_enabled")
    @Builder.Default
    private Boolean reportGenerationEnabled = false;

    @Column(name = "webhook_secret", length = 500)
    private String webhookSecret;

    @Column(name = "auth_token_hint", length = 100)
    private String authTokenHint;

    @Column(name = "contributors")
    @Builder.Default
    private Integer contributors = 0;

    @Column(name = "open_issues")
    @Builder.Default
    private Integer openIssues = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
