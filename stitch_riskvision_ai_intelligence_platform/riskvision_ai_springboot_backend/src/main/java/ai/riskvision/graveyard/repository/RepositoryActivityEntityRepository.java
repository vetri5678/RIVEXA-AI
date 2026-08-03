package ai.riskvision.graveyard.repository;

import ai.riskvision.graveyard.entity.RepositoryActivityEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RepositoryActivityEntityRepository extends JpaRepository<RepositoryActivityEntity, UUID> {

    List<RepositoryActivityEntity> findByRepositoryIdOrderByCreatedAtDesc(UUID repositoryId);

    List<RepositoryActivityEntity> findByRepositoryIdOrderByCreatedAtDesc(UUID repositoryId, Pageable pageable);

    void deleteByRepositoryId(UUID repositoryId);

    long countByRepositoryId(UUID repositoryId);
}
