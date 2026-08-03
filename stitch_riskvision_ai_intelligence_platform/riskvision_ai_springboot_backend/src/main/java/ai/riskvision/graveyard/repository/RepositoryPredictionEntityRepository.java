package ai.riskvision.graveyard.repository;

import ai.riskvision.graveyard.entity.RepositoryPredictionEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepositoryPredictionEntityRepository extends JpaRepository<RepositoryPredictionEntity, UUID> {

    List<RepositoryPredictionEntity> findByRepositoryIdOrderByCreatedAtDesc(UUID repositoryId);

    List<RepositoryPredictionEntity> findByRepositoryIdOrderByCreatedAtDesc(UUID repositoryId, Pageable pageable);

    Optional<RepositoryPredictionEntity> findTopByRepositoryIdOrderByCreatedAtDesc(UUID repositoryId);

    void deleteByRepositoryId(UUID repositoryId);

    long countByRepositoryId(UUID repositoryId);

    @Query("SELECT SUM(1) FROM RepositoryPredictionEntity p WHERE p.repositoryId = :repoId")
    Long totalPredictionCount(@Param("repoId") UUID repositoryId);

    @Query("SELECT COUNT(DISTINCT p.repositoryId) FROM RepositoryPredictionEntity p")
    long countRepositoriesWithPredictions();
}
