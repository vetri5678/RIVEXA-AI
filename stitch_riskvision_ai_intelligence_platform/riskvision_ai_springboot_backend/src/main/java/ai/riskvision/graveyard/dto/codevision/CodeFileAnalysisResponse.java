package ai.riskvision.graveyard.dto.codevision;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeFileAnalysisResponse {
    private UUID id;
    private UUID analysisRunId;
    private UUID repositoryId;
    private String filePath;
    private String fileHash;
    private String language;
    private Integer linesOfCode;
    private Integer riskScore;
    private String severity;
    private Integer confidence;
    private String analysisType;
    private Map<String, Object> metrics;
    private String status;
    private Instant analyzedAt;
    private long findingCount;
    private List<CodeFindingResponse> findings;
}
