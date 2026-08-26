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

    Optional<RepositoryEntity> findFirstByRepositoryUrl(String repositoryUrl);
    default Optional<RepositoryEntity> findByRepositoryUrl(String repositoryUrl) {
        return findFirstByRepositoryUrl(repositoryUrl);
    }

    Optional<RepositoryEntity> findFirstByUser_IdAndGithubRepositoryId(UUID userId, String githubRepositoryId);
    default Optional<RepositoryEntity> findByUser_IdAndGithubRepositoryId(UUID userId, String githubRepositoryId) {
        return findFirstByUser_IdAndGithubRepositoryId(userId, githubRepositoryId);
    }

    Optional<RepositoryEntity> findFirstByUser_IdAndRepositoryUrl(UUID userId, String repositoryUrl);
    default Optional<RepositoryEntity> findByUser_IdAndRepositoryUrl(UUID userId, String repositoryUrl) {
        return findFirstByUser_IdAndRepositoryUrl(userId, repositoryUrl);
    }

    Optional<RepositoryEntity> findFirstByUser_IdAndRepositoryName(UUID userId, String repositoryName);
    default Optional<RepositoryEntity> findByUser_IdAndRepositoryName(UUID userId, String repositoryName) {
        return findFirstByUser_IdAndRepositoryName(userId, repositoryName);
    }

    Page<RepositoryEntity> findByStatusNot(String status, Pageable pageable);

    // ─── Global queries (all repos — used only where admin-level view is needed) ─

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

    // ─── Per-user scoped queries (primary queries for all dashboard + listing) ─

    @Query("""
        SELECT r FROM RepositoryEntity r
        WHERE r.user.id = :userId
          AND (:search IS NULL OR :search = '' OR
               LOWER(r.repositoryName) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(r.organization) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(r.description) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:status IS NULL OR :status = '' OR UPPER(r.status) = UPPER(:status))
          AND (:riskLevel IS NULL OR :riskLevel = '' OR UPPER(r.riskLevel) = UPPER(:riskLevel))
          AND (:predictionStatus IS NULL OR :predictionStatus = '' OR UPPER(r.predictionStatus) = UPPER(:predictionStatus))
          AND (:gitProvider IS NULL OR :gitProvider = '' OR UPPER(r.gitProvider) = UPPER(:gitProvider))
          AND (:language IS NULL OR :language = '' OR LOWER(r.language) = LOWER(:language))
          AND (:organization IS NULL OR :organization = '' OR LOWER(r.organization) = LOWER(:organization))
        """)
    Page<RepositoryEntity> findAllByUserWithFilters(
            @Param("userId") UUID userId,
            @Param("search") String search,
            @Param("status") String status,
            @Param("riskLevel") String riskLevel,
            @Param("predictionStatus") String predictionStatus,
            @Param("gitProvider") String gitProvider,
            @Param("language") String language,
            @Param("organization") String organization,
            Pageable pageable
    );

    @Query("""
        SELECT r FROM RepositoryEntity r
        WHERE r.id IN :repoIds
          AND (:search IS NULL OR :search = '' OR
               LOWER(r.repositoryName) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(r.organization) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(r.description) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:status IS NULL OR :status = '' OR UPPER(r.status) = UPPER(:status))
          AND (:riskLevel IS NULL OR :riskLevel = '' OR UPPER(r.riskLevel) = UPPER(:riskLevel))
          AND (:predictionStatus IS NULL OR :predictionStatus = '' OR UPPER(r.predictionStatus) = UPPER(:predictionStatus))
          AND (:gitProvider IS NULL OR :gitProvider = '' OR UPPER(r.gitProvider) = UPPER(:gitProvider))
          AND (:language IS NULL OR :language = '' OR LOWER(r.language) = LOWER(:language))
          AND (:organization IS NULL OR :organization = '' OR LOWER(r.organization) = LOWER(:organization))
        """)
    Page<RepositoryEntity> findAllByIdsWithFilters(
            @Param("repoIds") List<UUID> repoIds,
            @Param("search") String search,
            @Param("status") String status,
            @Param("riskLevel") String riskLevel,
            @Param("predictionStatus") String predictionStatus,
            @Param("gitProvider") String gitProvider,
            @Param("language") String language,
            @Param("organization") String organization,
            Pageable pageable
    );

    // ─── Global aggregate counts ──────────────────────────────────────────────

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

    @Query("SELECT AVG(r.aiConfidence) FROM RepositoryEntity r WHERE r.status != 'ARCHIVED'")
    Double avgAiConfidence();

    List<RepositoryEntity> findByStatus(String status);

    // ─── Per-user aggregate counts (all dashboard methods MUST use these) ─────

    @Query("SELECT COUNT(r) FROM RepositoryEntity r WHERE r.user.id = :userId")
    long countByUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(r) FROM RepositoryEntity r WHERE r.user.id = :userId AND UPPER(r.riskLevel) = UPPER(:riskLevel) AND UPPER(r.status) != 'ARCHIVED'")
    long countByUserIdAndRiskLevelIgnoreCase(@Param("userId") UUID userId, @Param("riskLevel") String riskLevel);

    @Query("SELECT COUNT(r) FROM RepositoryEntity r WHERE r.user.id = :userId AND UPPER(r.predictionStatus) != 'PENDING'")
    long countByUserIdWithPredictions(@Param("userId") UUID userId);

    @Query("SELECT COUNT(r) FROM RepositoryEntity r WHERE r.user.id = :userId AND (r.healthScore >= 70.0 OR UPPER(r.riskLevel) = 'LOW') AND UPPER(r.status) != 'ARCHIVED'")
    long countHealthyByUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(r) FROM RepositoryEntity r WHERE r.user.id = :userId AND ((r.healthScore >= 40.0 AND r.healthScore < 70.0) OR UPPER(r.riskLevel) = 'MEDIUM') AND UPPER(r.status) != 'ARCHIVED'")
    long countUnderObservationByUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(r) FROM RepositoryEntity r WHERE r.user.id = :userId AND (UPPER(r.predictionStatus) = 'DEAD' OR UPPER(r.riskLevel) = 'CRITICAL' OR r.failureProbability >= 0.75) AND UPPER(r.status) != 'ARCHIVED'")
    long countPredictedDeadByUserId(@Param("userId") UUID userId);

    @Query("SELECT AVG(r.healthScore) FROM RepositoryEntity r WHERE r.user.id = :userId AND UPPER(r.status) != 'ARCHIVED'")
    Double avgHealthScoreByUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(r) FROM RepositoryEntity r WHERE r.user.id = :userId AND UPPER(r.riskLevel) = UPPER(:riskLevel) AND UPPER(r.status) != 'ARCHIVED'")
    long countByUserIdAndRiskLevel(@Param("userId") UUID userId, @Param("riskLevel") String riskLevel);

    @Query("SELECT AVG(r.failureProbability) FROM RepositoryEntity r WHERE r.user.id = :userId AND UPPER(r.status) != 'ARCHIVED'")
    Double avgFailureProbabilityByUserId(@Param("userId") UUID userId);

    @Query("SELECT AVG(r.aiConfidence) FROM RepositoryEntity r WHERE r.user.id = :userId AND UPPER(r.status) != 'ARCHIVED'")
    Double avgAiConfidenceByUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(r) FROM RepositoryEntity r WHERE r.user.id = :userId AND UPPER(r.status) = UPPER(:status)")
    long countByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") String status);

    @Query("SELECT r FROM RepositoryEntity r WHERE r.user.id = :userId AND UPPER(r.status) = 'ACTIVE' ORDER BY r.failureProbability DESC")
    List<RepositoryEntity> findTop5ByUserIdAndStatusActive(@Param("userId") UUID userId, Pageable pageable);


    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM RepositoryEntity r WHERE r.user.id = :userId AND LOWER(r.gitProvider) = LOWER(:gitProvider)")
    void deleteByUserIdAndGitProvider(@Param("userId") UUID userId, @Param("gitProvider") String gitProvider);

    @Query("SELECT r FROM RepositoryEntity r WHERE r.user.id = :userId AND LOWER(r.gitProvider) = LOWER(:gitProvider)")
    List<RepositoryEntity> findByUserIdAndGitProvider(@Param("userId") UUID userId, @Param("gitProvider") String gitProvider);

    @Query("SELECT COUNT(r) FROM RepositoryEntity r WHERE r.user.id = :userId AND LOWER(r.gitProvider) = LOWER(:gitProvider)")
    long countByUserIdAndGitProvider(@Param("userId") UUID userId, @Param("gitProvider") String gitProvider);

    @org.springframework.data.jpa.repository.Modifying
    @Query("""
        UPDATE RepositoryEntity r
        SET r.failureProbability = :failureProbability,
            r.healthScore = :healthScore,
            r.riskLevel = :riskLevel,
            r.aiConfidence = :aiConfidence,
            r.predictionStatus = :predictionStatus,
            r.updatedAt = CURRENT_TIMESTAMP
        WHERE r.id = :id
        """)
    int updatePredictionResults(
            @Param("id") UUID id,
            @Param("failureProbability") Double failureProbability,
            @Param("healthScore") Double healthScore,
            @Param("riskLevel") String riskLevel,
            @Param("aiConfidence") Double aiConfidence,
            @Param("predictionStatus") String predictionStatus
    );
}
