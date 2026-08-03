package ai.riskvision.graveyard.repository;

import ai.riskvision.graveyard.model.PredictionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PredictionRecordRepository extends JpaRepository<PredictionRecord, String> {

    List<PredictionRecord> findTop10ByOrderByFailureProbabilityDesc();

    @Query("SELECT AVG(p.failureProbability) FROM PredictionRecord p")
    Double getAverageFailureProbability();

    @Query("SELECT COUNT(p) FROM PredictionRecord p WHERE p.riskLevel = 'CRITICAL'")
    Long countCriticalProjects();
}
