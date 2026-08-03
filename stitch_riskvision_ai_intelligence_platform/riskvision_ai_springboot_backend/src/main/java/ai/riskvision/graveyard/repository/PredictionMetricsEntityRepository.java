package ai.riskvision.graveyard.repository;

import ai.riskvision.graveyard.entity.PredictionMetricsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface PredictionMetricsEntityRepository extends JpaRepository<PredictionMetricsEntity, UUID> {
}
