package ai.riskvision.graveyard.repository;

import ai.riskvision.graveyard.entity.AuditLogEntity;
import ai.riskvision.graveyard.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {

    List<AuditLogEntity> findByUserOrderByCreatedAtDesc(UserEntity user);

    List<AuditLogEntity> findByEventTypeOrderByCreatedAtDesc(String eventType);

    Page<AuditLogEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AuditLogEntity> findBySeverityOrderByCreatedAtDesc(String severity, Pageable pageable);

    Page<AuditLogEntity> findByModuleOrderByCreatedAtDesc(String module, Pageable pageable);

    Page<AuditLogEntity> findByEventTypeContainingIgnoreCaseOrderByCreatedAtDesc(String eventType, Pageable pageable);

    @Query("SELECT a FROM AuditLogEntity a WHERE " +
           "(:severity IS NULL OR a.severity = :severity) AND " +
           "(:module IS NULL OR a.module = :module) AND " +
           "(:eventType IS NULL OR LOWER(a.eventType) LIKE LOWER(CONCAT('%', :eventType, '%'))) AND " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:startDate IS NULL OR a.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR a.createdAt <= :endDate) " +
           "ORDER BY a.createdAt DESC")
    Page<AuditLogEntity> findFiltered(
            @Param("severity") String severity,
            @Param("module") String module,
            @Param("eventType") String eventType,
            @Param("status") String status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    @Query("SELECT a.eventType, COUNT(a) FROM AuditLogEntity a GROUP BY a.eventType ORDER BY COUNT(a) DESC")
    List<Object[]> countByEventType();

    @Query("SELECT a.severity, COUNT(a) FROM AuditLogEntity a GROUP BY a.severity")
    List<Object[]> countBySeverity();

    @Query("SELECT a.module, COUNT(a) FROM AuditLogEntity a GROUP BY a.module")
    List<Object[]> countByModule();

    @Query("SELECT COUNT(a) FROM AuditLogEntity a WHERE a.createdAt >= :since")
    long countSince(@Param("since") LocalDateTime since);

    List<AuditLogEntity> findTop20ByOrderByCreatedAtDesc();
}
