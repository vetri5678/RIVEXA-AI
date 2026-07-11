package ai.riskvision.graveyard.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "repository_metrics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepositoryMetricsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Column(name = "commit_count")
    @Builder.Default
    private Integer commitCount = 0;

    @Column(name = "commit_frequency")
    @Builder.Default
    private Double commitFrequency = 0.0;

    @Column(name = "pull_requests")
    @Builder.Default
    private Integer pullRequests = 0;

    @Column(name = "merged_pull_requests")
    @Builder.Default
    private Integer mergedPullRequests = 0;

    @Column(name = "failed_pull_requests")
    @Builder.Default
    private Integer failedPullRequests = 0;

    @Column(name = "contributors")
    @Builder.Default
    private Integer contributors = 0;

    @Column(name = "active_contributors")
    @Builder.Default
    private Integer activeContributors = 0;

    @Column(name = "inactive_days")
    @Builder.Default
    private Integer inactiveDays = 0;

    @Column(name = "open_issues")
    @Builder.Default
    private Integer openIssues = 0;

    @Column(name = "closed_issues")
    @Builder.Default
    private Integer closedIssues = 0;

    @Column(name = "code_coverage")
    @Builder.Default
    private Double codeCoverage = 0.0;

    @Column(name = "documentation_score")
    @Builder.Default
    private Double documentationScore = 0.0;

    @Column(name = "build_success_rate")
    @Builder.Default
    private Double buildSuccessRate = 0.0;

    @Column(name = "cyclomatic_complexity")
    @Builder.Default
    private Double cyclomaticComplexity = 0.0;

    @Column(name = "technical_debt")
    @Builder.Default
    private Double technicalDebt = 0.0;

    @Column(name = "bus_factor")
    @Builder.Default
    private Integer busFactor = 1;

    @Column(name = "velocity")
    @Builder.Default
    private Double velocity = 0.0;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
