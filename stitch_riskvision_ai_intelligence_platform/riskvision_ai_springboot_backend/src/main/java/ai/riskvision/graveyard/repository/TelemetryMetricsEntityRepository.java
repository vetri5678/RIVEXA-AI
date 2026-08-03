package ai.riskvision.graveyard.repository;

import ai.riskvision.graveyard.entity.TelemetryMetricsEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TelemetryMetricsEntityRepository extends JpaRepository<TelemetryMetricsEntity, UUID> {

    Optional<TelemetryMetricsEntity> findTopByOrderByTimestampDesc();

    Page<TelemetryMetricsEntity> findAllByOrderByTimestampDesc(Pageable pageable);
}
