package ai.riskvision.graveyard.repository;

import ai.riskvision.graveyard.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmail(String email);
    Optional<UserEntity> findByUsername(String username);
    Optional<UserEntity> findByResetToken(String resetToken);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);

    @Query("SELECT u FROM UserEntity u WHERE " +
           "(:query IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "AND (:role IS NULL OR u.role = :role) " +
           "AND (:isActive IS NULL OR u.isActive = :isActive)")
    Page<UserEntity> searchUsers(
            @Param("query") String query,
            @Param("role") String role,
            @Param("isActive") Boolean isActive,
            Pageable pageable);
}

