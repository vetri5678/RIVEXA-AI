package ai.riskvision.graveyard.dto.codevision;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeVisionSummaryResponse {
    private UUID repositoryId;
    private CodeAnalysisRunResponse latestRun;
    private long totalFilesDiscovered;
    private long totalFilesAnalyzed;
    private long filesWithFindings;
    private long criticalCount;
    private long highCount;
    private long mediumCount;
    private long lowCount;
    private Double failureProbability;
    private Integer riskScore;
    private String riskLevel;
    private Double healthScore;
    private Double aiConfidence;
    private String modelVersion;
    private Object featureImportance;
    private Map<String, Long> languageBreakdown;
    private Map<String, Long> findingTypeBreakdown;
}
