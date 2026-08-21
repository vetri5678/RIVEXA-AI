package ai.riskvision.graveyard.repository;

import ai.riskvision.graveyard.entity.CodeFileAnalysisEntity;
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
public interface CodeFileAnalysisRepository extends JpaRepository<CodeFileAnalysisEntity, UUID> {
    List<CodeFileAnalysisEntity> findByAnalysisRunId(UUID analysisRunId);
    Page<CodeFileAnalysisEntity> findByAnalysisRunId(UUID analysisRunId, Pageable pageable);

    @Query("SELECT f FROM CodeFileAnalysisEntity f WHERE f.analysisRunId = :runId AND (:severity IS NULL OR f.severity = :severity) AND (:language IS NULL OR f.language = :language) AND (:search IS NULL OR LOWER(f.filePath) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<CodeFileAnalysisEntity> findByRunIdWithFilters(
            @Param("runId") UUID runId,
            @Param("severity") String severity,
            @Param("language") String language,
            @Param("search") String search,
            Pageable pageable
    );

    Optional<CodeFileAnalysisEntity> findTopByRepositoryIdAndFilePathOrderByAnalyzedAtDesc(UUID repositoryId, String filePath);

    @Query("SELECT COUNT(f) FROM CodeFileAnalysisEntity f WHERE f.analysisRunId = :runId AND f.severity = :severity")
    long countByAnalysisRunIdAndSeverity(@Param("runId") UUID runId, @Param("severity") String severity);
}
