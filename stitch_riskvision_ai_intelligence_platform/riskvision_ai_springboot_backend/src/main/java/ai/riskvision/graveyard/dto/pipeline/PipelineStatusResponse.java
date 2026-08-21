package ai.riskvision.graveyard.dto.pipeline;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineStatusResponse {
    private String status;
    private String modelVersion;
    private String loadedModel;
    private Boolean trained;
    private Boolean databaseConnected;
    private String activeStage;
    private LocalDateTime timestamp;
    private Integer reportsCount;
    private Double accuracy;
    private Map<String, Object> metrics;
    private List<PipelineStageDTO> stages;
}
