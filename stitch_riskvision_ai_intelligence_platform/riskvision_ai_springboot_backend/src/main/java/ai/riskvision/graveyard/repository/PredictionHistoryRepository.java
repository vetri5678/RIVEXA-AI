package ai.riskvision.graveyard.repository;

import ai.riskvision.graveyard.entity.PredictionHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface PredictionHistoryRepository extends JpaRepository<PredictionHistoryEntity, String> {

    List<PredictionHistoryEntity> findByProjectIdOrderByCreatedAtDesc(String projectId);

    List<PredictionHistoryEntity> findTop50ByOrderByCreatedAtDesc();

    long countByRiskLevel(String riskLevel);

    @Query("SELECT p.riskLevel, COUNT(p) FROM PredictionHistoryEntity p GROUP BY p.riskLevel")
    List<Object[]> countByRiskLevelGrouped();

    @Query("SELECT AVG(p.confidence) FROM PredictionHistoryEntity p")
    Double findAverageConfidence();

    @Query("SELECT AVG(p.riskScore) FROM PredictionHistoryEntity p")
    Double findAverageRiskScore();

    long countByCreatedAtBetween(ZonedDateTime from, ZonedDateTime to);

    @Query("SELECT p FROM PredictionHistoryEntity p WHERE p.createdAt >= :since ORDER BY p.createdAt DESC")
    List<PredictionHistoryEntity> findRecentSince(@Param("since") ZonedDateTime since);
}
