package ai.riskvision.graveyard.repository;

import ai.riskvision.graveyard.entity.RepositoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepositoryEntityRepository extends JpaRepository<RepositoryEntity, UUID> {

    boolean existsByRepositoryUrlAndIdNot(String repositoryUrl, UUID id);

    boolean existsByRepositoryUrl(String repositoryUrl);

    boolean existsByRepositoryNameAndOrganization(String repositoryName, String organization);

    Optional<RepositoryEntity> findByRepositoryUrl(String repositoryUrl);

    Page<RepositoryEntity> findByStatusNot(String status, Pageable pageable);

    @Query("""
        SELECT r FROM RepositoryEntity r
        WHERE (:search IS NULL OR :search = '' OR
               LOWER(r.repositoryName) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(r.organization) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(r.description) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:status IS NULL OR :status = '' OR r.status = :status)
          AND (:riskLevel IS NULL OR :riskLevel = '' OR r.riskLevel = :riskLevel)
          AND (:predictionStatus IS NULL OR :predictionStatus = '' OR r.predictionStatus = :predictionStatus)
          AND (:gitProvider IS NULL OR :gitProvider = '' OR r.gitProvider = :gitProvider)
          AND (:language IS NULL OR :language = '' OR r.language = :language)
          AND (:organization IS NULL OR :organization = '' OR r.organization = :organization)
        """)
    Page<RepositoryEntity> findAllWithFilters(
            @Param("search") String search,
            @Param("status") String status,
            @Param("riskLevel") String riskLevel,
            @Param("predictionStatus") String predictionStatus,
            @Param("gitProvider") String gitProvider,
            @Param("language") String language,
            @Param("organization") String organization,
            Pageable pageable
    );

    @Query("SELECT COUNT(r) FROM RepositoryEntity r WHERE r.status = :status")
    long countByStatus(@Param("status") String status);

    @Query("SELECT COUNT(r) FROM RepositoryEntity r WHERE r.riskLevel = :riskLevel AND r.status != 'ARCHIVED'")
    long countByRiskLevel(@Param("riskLevel") String riskLevel);

    @Query("SELECT COUNT(r) FROM RepositoryEntity r WHERE r.predictionStatus = :predictionStatus")
    long countByPredictionStatus(@Param("predictionStatus") String predictionStatus);

    @Query("SELECT COUNT(r) FROM RepositoryEntity r WHERE r.healthScore >= 70 AND r.status = 'ACTIVE'")
    long countHealthy();

    @Query("SELECT COUNT(r) FROM RepositoryEntity r WHERE r.healthScore >= 40 AND r.healthScore < 70 AND r.status = 'ACTIVE'")
    long countUnderObservation();

    @Query("SELECT AVG(r.healthScore) FROM RepositoryEntity r WHERE r.status != 'ARCHIVED'")
    Double avgHealthScore();

    @Query("SELECT AVG(r.failureProbability) FROM RepositoryEntity r WHERE r.status != 'ARCHIVED'")
    Double avgFailureProbability();

    @Query("SELECT COUNT(r) FROM RepositoryEntity r WHERE r.predictionStatus != 'PENDING'")
    long countWithPredictions();

    List<RepositoryEntity> findTop5ByStatusOrderByFailureProbabilityDesc(String status);

    List<RepositoryEntity> findByOrganization(String organization);

    List<RepositoryEntity> findByStatus(String status);
}
