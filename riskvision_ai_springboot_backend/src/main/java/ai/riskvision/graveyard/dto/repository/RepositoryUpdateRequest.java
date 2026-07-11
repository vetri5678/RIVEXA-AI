package ai.riskvision.graveyard.dto.repository;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepositoryUpdateRequest {

    @Size(min = 2, max = 200, message = "Repository name must be between 2 and 200 characters")
    private String repositoryName;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    @Size(max = 200, message = "Organization cannot exceed 200 characters")
    private String organization;

    @Size(max = 200, message = "Owner cannot exceed 200 characters")
    private String owner;

    @Size(max = 500, message = "URL cannot exceed 500 characters")
    private String repositoryUrl;

    private String gitProvider;

    @Size(max = 100, message = "Branch name cannot exceed 100 characters")
    private String branch;

    private String technology;
    private String language;
    private String projectType;
    private String visibility;
    private String license;
    private String predictionFrequency;
    private Boolean autoPredictionEnabled;
    private Boolean notificationsEnabled;
    private Boolean backgroundSyncEnabled;
    private Boolean reportGenerationEnabled;
    private String authTokenHint;
    private String webhookSecret;
}
