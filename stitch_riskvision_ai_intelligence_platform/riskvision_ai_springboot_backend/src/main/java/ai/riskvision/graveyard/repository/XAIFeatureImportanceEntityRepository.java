package ai.riskvision.graveyard.repository;

import ai.riskvision.graveyard.entity.XAIFeatureImportanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface XAIFeatureImportanceEntityRepository extends JpaRepository<XAIFeatureImportanceEntity, UUID> {
}
