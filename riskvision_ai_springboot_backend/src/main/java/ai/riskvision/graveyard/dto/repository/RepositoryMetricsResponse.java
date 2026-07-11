package ai.riskvision.graveyard.dto.repository;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepositoryMetricsResponse {

    private UUID id;
    private UUID repositoryId;
    private Integer commitCount;
    private Double commitFrequency;
    private Integer pullRequests;
    private Integer mergedPullRequests;
    private Integer failedPullRequests;
    private Integer contributors;
    private Integer activeContributors;
    private Integer inactiveDays;
    private Integer openIssues;
    private Integer closedIssues;
    private Double codeCoverage;
    private Double documentationScore;
    private Double buildSuccessRate;
    private Double cyclomaticComplexity;
    private Double technicalDebt;
    private Integer busFactor;
    private Double velocity;
    private LocalDateTime updatedAt;
}
