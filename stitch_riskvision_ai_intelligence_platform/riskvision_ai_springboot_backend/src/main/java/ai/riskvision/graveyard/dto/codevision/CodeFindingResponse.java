package ai.riskvision.graveyard.dto.codevision;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeFindingResponse {
    private UUID id;
    private UUID fileAnalysisId;
    private UUID analysisRunId;
    private String findingType;
    private String severity;
    private Integer confidence;
    private String symbolName;
    private Integer startLine;
    private Integer endLine;
    private String title;
    private String description;
    private String evidence;
    private String recommendation;
    private String analysisSource;
    private Instant createdAt;
}
