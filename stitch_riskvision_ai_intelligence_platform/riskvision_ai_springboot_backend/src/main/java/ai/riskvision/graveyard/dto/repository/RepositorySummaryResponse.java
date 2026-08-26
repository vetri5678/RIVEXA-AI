package ai.riskvision.graveyard.dto.repository;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepositorySummaryResponse {

    private UUID id;
    private String repositoryName;
    private String organization;
    private String description;
    private String technology;
    private String language;
    private String repositoryUrl;
    private String gitProvider;
    private String branch;
    private String status;
    private Double healthScore;
    private Double failureProbability;
    private String predictionStatus;
    private Integer contributors;
    private Integer openIssues;
    private Integer commitCount;
    private Integer pullRequests;
    private Double buildSuccessRate;
    private LocalDateTime lastCommitDate;
    private LocalDateTime lastSyncDate;
    private String lifecycleStage;
    private Double aiConfidence;
    private String riskLevel;
    private LocalDateTime createdAt;
}
