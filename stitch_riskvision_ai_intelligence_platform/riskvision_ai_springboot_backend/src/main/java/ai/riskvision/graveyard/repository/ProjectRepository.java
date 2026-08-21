package ai.riskvision.graveyard.repository;

import ai.riskvision.graveyard.entity.ProjectEntity;
import ai.riskvision.graveyard.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {
    
    @Query("SELECT p FROM ProjectEntity p WHERE " +
           "(p.ownerUuid = :owner OR p.owner = :owner) AND " +
           "(:search IS NULL OR :search = '' OR LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:status IS NULL OR :status = '' OR p.status = :status)")
    Page<ProjectEntity> findByOwnerAndFilters(
            @Param("owner") UserEntity owner,
            @Param("search") String search,
            @Param("status") String status,
            Pageable pageable
    );

    long countByOwnerUuidOrOwner(UserEntity ownerUuid, UserEntity owner);

    @Query("SELECT p.externalId FROM ProjectEntity p WHERE p.ownerUuid = :owner OR p.owner = :owner")
    java.util.List<String> findExternalIdsByOwner(@Param("owner") UserEntity owner);

    @Query("SELECT COUNT(p) > 0 FROM ProjectEntity p WHERE p.externalId = :externalId AND (p.ownerUuid = :owner OR p.owner = :owner)")
    boolean existsByExternalIdAndOwner(@Param("externalId") String externalId, @Param("owner") UserEntity owner);
}
