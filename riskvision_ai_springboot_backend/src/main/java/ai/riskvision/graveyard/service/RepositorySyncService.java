package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.entity.RepositoryActivityEntity;
import ai.riskvision.graveyard.entity.RepositoryEntity;
import ai.riskvision.graveyard.repository.RepositoryActivityEntityRepository;
import ai.riskvision.graveyard.repository.RepositoryEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepositorySyncService {

    private final RepositoryEntityRepository repoRepository;
    private final RepositoryActivityEntityRepository activityRepository;

    /**
     * Performs a repository sync — updates lastSyncDate, refreshes basic metadata.
     * Designed to be extended with Git provider webhooks/API calls in the future.
     */
    @Transactional
    public void syncRepository(UUID repositoryId, String actor) {
        RepositoryEntity entity = repoRepository.findById(repositoryId)
                .orElseThrow(() -> new NoSuchElementException("Repository not found: " + repositoryId));

        // Update sync timestamp — real implementation would call Git provider API here
        entity.setLastSyncDate(LocalDateTime.now());
        repoRepository.save(entity);

        logActivity(repositoryId, "REPOSITORY_SYNCED",
                "Repository '" + entity.getRepositoryName() + "' synchronized with " + entity.getGitProvider(),
                actor, "SYNC", "INFO");

        log.info("Repository synced: {} by {}", repositoryId, actor);
    }

    /**
     * Logs an activity event for a repository — used by all services to maintain audit trail.
     */
    @Transactional
    public void logActivity(UUID repositoryId, String action, String description,
                            String actor, String resourceType, String severity) {
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
