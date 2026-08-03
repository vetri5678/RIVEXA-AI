package ai.riskvision.graveyard.repository;

import ai.riskvision.graveyard.entity.ModelPerformanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface ModelPerformanceEntityRepository extends JpaRepository<ModelPerformanceEntity, UUID> {
}
