package ai.riskvision.graveyard.repository;

import ai.riskvision.graveyard.entity.RepositoryMetricsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepositoryMetricsEntityRepository extends JpaRepository<RepositoryMetricsEntity, UUID> {

    Optional<RepositoryMetricsEntity> findFirstByRepositoryId(UUID repositoryId);

    default Optional<RepositoryMetricsEntity> findByRepositoryId(UUID repositoryId) {
        return findFirstByRepositoryId(repositoryId);
    }

    void deleteByRepositoryId(UUID repositoryId);

    boolean existsByRepositoryId(UUID repositoryId);
}
