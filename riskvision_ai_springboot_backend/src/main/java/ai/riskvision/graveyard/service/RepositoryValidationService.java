package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.dto.repository.RepositoryCreateRequest;
import ai.riskvision.graveyard.dto.repository.RepositoryUpdateRequest;
import ai.riskvision.graveyard.entity.RepositoryEntity;
import ai.riskvision.graveyard.repository.RepositoryEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepositoryValidationService {

    private final RepositoryEntityRepository repoRepository;

    public void validateCreate(RepositoryCreateRequest request) {
        // Check for duplicate URL
        if (repoRepository.existsByRepositoryUrl(request.getRepositoryUrl())) {
            throw new IllegalArgumentException(
                    "A repository with URL '" + request.getRepositoryUrl() + "' already exists");
        }

        // Check for duplicate name + org combination
        if (request.getOrganization() != null && !request.getOrganization().isBlank()) {
            if (repoRepository.existsByRepositoryNameAndOrganization(
                    request.getRepositoryName(), request.getOrganization())) {
                throw new IllegalArgumentException(
                        "A repository named '" + request.getRepositoryName()
                                + "' already exists in organization '" + request.getOrganization() + "'");
            }
        }

        // Validate Git provider
        validateGitProvider(request.getGitProvider());

        // Validate URL format
        validateUrl(request.getRepositoryUrl());

        // Validate prediction frequency if provided
        if (request.getPredictionFrequency() != null) {
            validatePredictionFrequency(request.getPredictionFrequency());
        }
    }

    public void validateUpdate(UUID id, RepositoryUpdateRequest request, RepositoryEntity existing) {
        // Check for duplicate URL if URL is being changed
        if (request.getRepositoryUrl() != null
                && !request.getRepositoryUrl().equals(existing.getRepositoryUrl())) {
            if (repoRepository.existsByRepositoryUrlAndIdNot(request.getRepositoryUrl(), id)) {
                throw new IllegalArgumentException(
                        "A repository with URL '" + request.getRepositoryUrl() + "' already exists");
            }
            validateUrl(request.getRepositoryUrl());
        }

        // Validate Git provider if being changed
        if (request.getGitProvider() != null) {
            validateGitProvider(request.getGitProvider());
        }

        // Validate prediction frequency if being changed
        if (request.getPredictionFrequency() != null) {
            validatePredictionFrequency(request.getPredictionFrequency());
        }
    }

    public boolean validateConnectionToken(String gitProvider, String token, String repositoryUrl) {
        // Design hook for future OAuth/token validation against Git providers
        // Currently validates basic token presence and length
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        if (token.trim().length() < 10) {
            return false;
        }
        log.info("Token validation requested for provider={} url={}", gitProvider, repositoryUrl);
        return true;
    }

    private void validateGitProvider(String gitProvider) {
        if (gitProvider == null) return;
        java.util.Set<String> valid = java.util.Set.of("GITHUB", "GITLAB", "BITBUCKET", "AZURE_DEVOPS", "OTHER");
        if (!valid.contains(gitProvider.toUpperCase())) {
            throw new IllegalArgumentException(
                    "Invalid Git provider: " + gitProvider + ". Must be one of: " + valid);
        }
    }

    private void validateUrl(String url) {
        if (url == null || url.isBlank()) return;
        if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("git@")) {
            throw new IllegalArgumentException(
                    "Repository URL must start with http://, https://, or git@");
        }
    }

    private void validatePredictionFrequency(String frequency) {
        java.util.Set<String> valid = java.util.Set.of("DAILY", "WEEKLY", "MONTHLY", "MANUAL");
        if (!valid.contains(frequency.toUpperCase())) {
            throw new IllegalArgumentException(
                    "Invalid prediction frequency: " + frequency + ". Must be one of: " + valid);
        }
    }
}
