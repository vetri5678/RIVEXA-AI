package ai.riskvision.graveyard.dto.pipeline;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineStageDTO {
    private String name;
    private String status; // RUNNING, COMPLETED, PENDING, FAILED
    private double progressPct;
    private long durationSeconds;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean currentStage;
}
