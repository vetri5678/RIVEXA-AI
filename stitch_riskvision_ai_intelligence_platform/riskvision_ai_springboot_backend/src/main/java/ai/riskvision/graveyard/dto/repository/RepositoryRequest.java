package ai.riskvision.graveyard.dto.repository;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepositoryRequest {
    @NotBlank(message = "Repository name is required")
    private String repositoryName;

    @NotBlank(message = "Repository URL is required")
    private String repositoryUrl;

    @NotBlank(message = "Git provider is required")
    private String gitProvider;

    private String description;
    private String organization;
    private String branch;
    private String visibility;
}
