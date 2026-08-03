package ai.riskvision.graveyard.dto.prediction;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * PredictionRunRequest — body for POST /api/v1/predictions/run
 *
 * <p>Accepts the database UUID of the repository to run the AI prediction on.
 * Spring Boot will validate, invoke RepoPredictionService, and return a full result.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PredictionRunRequest {

    /**
     * The database UUID of the repository to predict.
     * Must match an existing entry in the {@code repositories} table.
     */
    @NotNull(message = "repositoryId is required")
    private UUID repositoryId;
}
