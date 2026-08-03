package ai.riskvision.graveyard.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIAnalysisResultDTO {
    private String summary;
    private String severity;
    private String confidence;
    private String rootCause;
    private List<String> recommendations;
    
    // Add additional properties for flexible features if needed
    private String impact;
    private String recommendedFix;
    private String reason;
    private String riskScore;
}
