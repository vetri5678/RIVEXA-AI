package ai.riskvision.graveyard.dto.repository;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepositoryDetailResponse {

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

    // Embedded sub-objects
    private RepositoryMetricsResponse metrics;
    private RepositoryPredictionResponse latestPrediction;
    private List<RepositoryPredictionResponse> predictionHistory;
    private List<RepositoryActivityResponse> recentActivities;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RepositoryPredictionResponse {
        private UUID id;
        private Double failureProbability;
        private Integer riskScore;
        private String riskLevel;
        private Double confidence;
        private Double healthScore;
        private String modelVersion;
        private String predictionStatus;
        private String featureImportanceJson;
        private String recommendationsJson;
        private String triggeredBy;
        private LocalDateTime createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RepositoryActivityResponse {
        private UUID id;
        private String action;
        private String description;
        private String actor;
        private String resourceType;
        private String severity;
        private LocalDateTime createdAt;
    }
}
