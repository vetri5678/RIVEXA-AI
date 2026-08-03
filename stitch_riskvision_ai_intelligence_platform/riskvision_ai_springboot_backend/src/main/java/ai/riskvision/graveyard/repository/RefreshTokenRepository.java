package ai.riskvision.graveyard.repository;

import ai.riskvision.graveyard.entity.RefreshTokenEntity;
import ai.riskvision.graveyard.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);
    List<RefreshTokenEntity> findByUserAndRevokedFalse(UserEntity user);
    void deleteByUser(UserEntity user);
    void deleteByExpiresAtBefore(LocalDateTime dateTime);
}

