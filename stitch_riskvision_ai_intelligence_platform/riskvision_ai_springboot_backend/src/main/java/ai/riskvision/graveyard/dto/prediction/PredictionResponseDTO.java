package ai.riskvision.graveyard.dto.prediction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionResponseDTO {
    private String id;
    private String riskLevel;
    private Double riskScore;
    private Double confidence;
    private Double probability;
    private List<String> topFactors;
    private Map<String, Object> shapExplainability;
    private String modelVersion;
    private String predictionTime;
}
