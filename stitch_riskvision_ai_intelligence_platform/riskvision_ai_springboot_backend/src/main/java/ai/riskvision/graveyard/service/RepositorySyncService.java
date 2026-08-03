package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.client.GitHubClient;
import ai.riskvision.graveyard.entity.RepositoryActivityEntity;
import ai.riskvision.graveyard.entity.RepositoryEntity;
import ai.riskvision.graveyard.repository.RepositoryActivityEntityRepository;
import ai.riskvision.graveyard.repository.RepositoryEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepositorySyncService {

    private final RepositoryEntityRepository repoRepository;
    private final RepositoryActivityEntityRepository activityRepository;
    private final GitHubClient gitHubClient;
    private final RestTemplate restTemplate;

    @Value("${ml.service.url:http://localhost:8000}")
    private String mlServiceUrl;

    private static class RepoInfo {
        String owner;
        String repo;
        
        RepoInfo(String owner, String repo) {
            this.owner = owner;
            this.repo = repo;
        }
    }

    private RepoInfo parseUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        try {
            String cleanUrl = url.trim().replaceAll("/+$", "").replaceAll("\\.git$", "");
            String[] parts = cleanUrl.split("/");
            if (parts.length >= 2) {
                String repo = parts[parts.length - 1];
                String owner = parts[parts.length - 2];
                return new RepoInfo(owner, repo);
            }
        } catch (Exception e) {
            log.warn("Failed to parse repository URL: {}", url, e);
        }
        return null;
    }

    /**
     * Performs a repository sync — updates lastSyncDate, refreshes basic metadata via GitHubClient.
     */
    @Transactional
    public void syncRepository(UUID repositoryId, String actor) {
        if (repositoryId == null) {
            throw new IllegalArgumentException("Repository ID must not be null");
        }
        RepositoryEntity entity = repoRepository.findById(repositoryId)
                .orElseThrow(() -> new NoSuchElementException("Repository not found: " + repositoryId));

        entity.setLastSyncDate(LocalDateTime.now());
        
        String provider = entity.getGitProvider() != null ? entity.getGitProvider().toUpperCase() : "GITHUB";
        RepoInfo info = parseUrl(entity.getRepositoryUrl());

        if (info != null) {
            try {
                if ("GITHUB".equals(provider)) {
                    log.info("Fetching GitHub API metadata for {}/{} via GitHubClient", info.owner, info.repo);
                    Map<String, Object> body = gitHubClient.getRepositoryMetadata(info.owner, info.repo);
                    
                    if (body != null) {
                        if (body.containsKey("description") && body.get("description") != null) {
                            entity.setDescription((String) body.get("description"));
                        }
                        if (body.containsKey("language") && body.get("language") != null) {
                            entity.setLanguage((String) body.get("language"));
                        }
                        if (body.containsKey("open_issues_count")) {
                            entity.setOpenIssues(((Number) body.get("open_issues_count")).intValue());
                        }
                        if (body.containsKey("private")) {
                            entity.setVisibility(((Boolean) body.get("private")) ? "PRIVATE" : "PUBLIC");
                        }
                        
                        if (body.containsKey("license") && body.get("license") instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> licenseMap = (Map<String, Object>) body.get("license");
                            if (licenseMap.containsKey("name")) {
                                entity.setLicense((String) licenseMap.get("name"));
                            }
                        }
                    }

                    try {
                        List<Map<String, Object>> contribList = gitHubClient.getContributors(info.owner, info.repo);
                        if (contribList != null) {
                            entity.setContributors(contribList.size());
                        }
                    } catch (Exception ex) {
                        log.warn("Failed to fetch contributors count for GitHub repo {}/{}: {}", info.owner, info.repo, ex.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to sync metrics from Git API provider ({}). Error: {}", provider, e.getMessage());
                if (entity.getContributors() == null || entity.getContributors() == 0) {
                    entity.setContributors(1);
                }
            }
        }

        repoRepository.save(entity);

        syncRepositoryToFastApi(entity);

        logActivity(repositoryId, "REPOSITORY_SYNCED",
                "Repository '" + entity.getRepositoryName() + "' synchronized with " + entity.getGitProvider(),
                actor, "SYNC", "INFO");

        log.info("Repository synced: {} by {}", repositoryId, actor);
    }

    /**
     * Replicates repository metadata to Python FastAPI projects persistence endpoint.
     */
    public void syncRepositoryToFastApi(RepositoryEntity entity) {
        if (entity == null || mlServiceUrl == null) return;
        try {
            String url = mlServiceUrl + "/api/v1/projects";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> payload = new HashMap<>();
            payload.put("project_id", entity.getId() != null ? entity.getId().toString() : UUID.randomUUID().toString());
            payload.put("project_name", entity.getRepositoryName());
            payload.put("team_size", entity.getContributors() != null ? entity.getContributors() : 1);
            payload.put("budget", 100000.0);
            payload.put("actual_cost", 90000.0);
            payload.put("timeline_months", 12.0);
            payload.put("actual_duration", 10.0);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            restTemplate.postForLocation(url, request);
            log.info("Successfully synchronized repository {} to FastAPI projects database", entity.getRepositoryName());
        } catch (Exception e) {
            log.warn("FastAPI project sync skipped or unavailable: {}", e.getMessage());
        }
    }

    @Transactional
    public void syncAllRepositories() {
        List<RepositoryEntity> repositories = repoRepository.findAll();
        log.info("Starting background synchronization for {} repositories...", repositories.size());
        for (RepositoryEntity repo : repositories) {
            try {
                syncRepository(repo.getId(), "SYSTEM_SCHEDULED");
            } catch (Exception e) {
                log.error("Failed scheduled sync for repository {}: {}", repo.getId(), e.getMessage());
            }
        }
    }

    /**
     * Logs an activity event for a repository — used by all services to maintain audit trail.
     */
    @Transactional
    public void logActivity(UUID repositoryId, String action, String description,
                            String actor, String resourceType, String severity) {
        if (repositoryId == null) {
            throw new IllegalArgumentException("Repository ID must not be null");
        }
        RepositoryActivityEntity activity = RepositoryActivityEntity.builder()
                .repositoryId(repositoryId)
                .action(action)
                .description(description)
                .actor(actor != null ? actor : "SYSTEM")
                .resourceType(resourceType)
                .severity(severity != null ? severity : "INFO")
                .build();
        activityRepository.save(activity);
    }
}
