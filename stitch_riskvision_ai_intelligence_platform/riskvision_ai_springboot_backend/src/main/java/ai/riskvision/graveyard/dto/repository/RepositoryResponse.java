package ai.riskvision.graveyard.dto.repository;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepositoryResponse {

    private UUID id;
    private String repositoryName;
    private String description;
    private String organization;
    private String owner;
    private String repositoryUrl;
    private String gitProvider;
    private String branch;
    private String technology;
    private String language;
    private String projectType;
    private String visibility;
    private String license;
    private Double healthScore;
    private Double failureProbability;
    private String predictionStatus;
    private String lifecycleStage;
    private String status;
    private String riskLevel;
    private Double aiConfidence;
    private Integer contributors;
    private Integer openIssues;
    private LocalDateTime lastCommitDate;
    private LocalDateTime lastSyncDate;
    private String predictionFrequency;
    private Boolean autoPredictionEnabled;
    private Boolean notificationsEnabled;
    private Boolean backgroundSyncEnabled;
    private Boolean reportGenerationEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
