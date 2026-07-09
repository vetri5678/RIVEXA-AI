package ai.riskvision.graveyard.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardOverviewDTO {
    private Long totalProjects;
    private Long totalPredictions;
    private Long predictionsToday;
    private Long criticalProjects;
    private Double avgConfidence;
    private Double graveyardIndex;
    private Double healthScore;
}
