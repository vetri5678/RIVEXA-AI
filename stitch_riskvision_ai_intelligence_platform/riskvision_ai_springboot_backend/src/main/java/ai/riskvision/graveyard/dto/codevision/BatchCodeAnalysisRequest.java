package ai.riskvision.graveyard.dto.codevision;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchCodeAnalysisRequest {
    private List<UUID> repositoryIds;
    private boolean force;
}
