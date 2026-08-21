package ai.riskvision.graveyard.repository;

import ai.riskvision.graveyard.entity.CodeAnalysisRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CodeAnalysisRunRepository extends JpaRepository<CodeAnalysisRunEntity, UUID> {
    Optional<CodeAnalysisRunEntity> findTopByRepositoryIdAndUserIdOrderByCreatedAtDesc(UUID repositoryId, UUID userId);
    Optional<CodeAnalysisRunEntity> findTopByRepositoryIdAndUserIdAndStatusOrderByCreatedAtDesc(UUID repositoryId, UUID userId, String status);
    List<CodeAnalysisRunEntity> findByRepositoryIdAndUserIdOrderByCreatedAtDesc(UUID repositoryId, UUID userId);
    List<CodeAnalysisRunEntity> findByRepositoryIdAndStatus(UUID repositoryId, String status);
}
