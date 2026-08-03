package ai.riskvision.graveyard.repository;

import ai.riskvision.graveyard.entity.UserEntity;
import ai.riskvision.graveyard.entity.VerificationTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationTokenEntity, UUID> {
    Optional<VerificationTokenEntity> findByToken(String token);
    Optional<VerificationTokenEntity> findByTokenAndTokenType(String token, String tokenType);
    List<VerificationTokenEntity> findByUserAndTokenTypeAndUsedFalse(UserEntity user, String tokenType);
    void deleteByUser(UserEntity user);
    void deleteByExpiresAtBefore(LocalDateTime dateTime);
}

