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
public class CodeAnalysisRunResponse {
    private UUID id;
    private UUID userId;
    private UUID repositoryId;
    private String status;
    private Instant startedAt;
    private Instant completedAt;
    private Integer filesDiscovered;
    private Integer filesAnalyzed;
    private Integer filesWithFindings;
    private String currentlyAnalyzingFile;
    private String errorMessage;
    private Instant createdAt;
}
