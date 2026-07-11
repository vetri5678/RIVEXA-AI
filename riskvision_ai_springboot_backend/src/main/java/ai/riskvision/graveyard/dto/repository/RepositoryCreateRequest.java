package ai.riskvision.graveyard.dto.repository;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepositoryCreateRequest {

    @NotBlank(message = "Repository name is required")
    @Size(min = 2, max = 200, message = "Repository name must be between 2 and 200 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_\\-. ]+$", message = "Repository name contains invalid characters")
    private String repositoryName;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    @Size(max = 200, message = "Organization cannot exceed 200 characters")
    private String organization;

    @Size(max = 200, message = "Owner cannot exceed 200 characters")
    private String owner;

    @NotBlank(message = "Repository URL is required")
    @Size(max = 500, message = "URL cannot exceed 500 characters")
    private String repositoryUrl;

    @NotBlank(message = "Git provider is required")
    @Pattern(regexp = "GITHUB|GITLAB|BITBUCKET|AZURE_DEVOPS|OTHER",
             message = "Git provider must be one of: GITHUB, GITLAB, BITBUCKET, AZURE_DEVOPS, OTHER")
    private String gitProvider;

    @Size(max = 100, message = "Branch name cannot exceed 100 characters")
    private String branch;

    private String technology;
    private String language;
    private String projectType;

    @Pattern(regexp = "PUBLIC|PRIVATE|INTERNAL", message = "Visibility must be PUBLIC, PRIVATE, or INTERNAL")
    private String visibility;

    private String license;
    private String predictionFrequency;
    private Boolean autoPredictionEnabled;
    private Boolean notificationsEnabled;
    private Boolean backgroundSyncEnabled;
    private Boolean reportGenerationEnabled;

    @Size(max = 500, message = "Auth token hint cannot exceed 500 characters")
    private String authTokenHint;

    @Size(max = 500, message = "Webhook secret cannot exceed 500 characters")
    private String webhookSecret;
}
