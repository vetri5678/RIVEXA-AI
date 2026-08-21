package ai.riskvision.graveyard.repository;

import ai.riskvision.graveyard.entity.CodeFindingEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CodeFindingRepository extends JpaRepository<CodeFindingEntity, UUID> {
    List<CodeFindingEntity> findByFileAnalysisIdOrderBySeverityDesc(UUID fileAnalysisId);
    List<CodeFindingEntity> findByAnalysisRunId(UUID analysisRunId);

    @Query("SELECT f FROM CodeFindingEntity f WHERE f.analysisRunId = :runId AND (:severity IS NULL OR f.severity = :severity) AND (:findingType IS NULL OR f.findingType = :findingType)")
    Page<CodeFindingEntity> findByRunIdWithFilters(
            @Param("runId") UUID runId,
            @Param("severity") String severity,
            @Param("findingType") String findingType,
            Pageable pageable
    );

    long countByFileAnalysisId(UUID fileAnalysisId);
    long countByAnalysisRunId(UUID analysisRunId);
}
