package ai.riskvision.graveyard.repository;

import ai.riskvision.graveyard.entity.SystemMetricsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface SystemMetricsEntityRepository extends JpaRepository<SystemMetricsEntity, UUID> {
}
