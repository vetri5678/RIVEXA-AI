package ai.riskvision.graveyard.dto.repository;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepositoryStatisticsResponse {

    private Long total;
    private Long healthy;
    private Long underObservation;
    private Long highRisk;
    private Long predictedDead;
    private Long archived;
    private Long active;
    private Long pendingPrediction;
    private Double aiCoveragePercent;
    private Double avgHealthScore;
    private Double avgFailureProbability;
    private Long totalPredictionsRun;
    private String lastSyncTime;
}
