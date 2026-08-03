package ai.riskvision.graveyard.repository;

import ai.riskvision.graveyard.entity.OAuthAccountEntity;
import ai.riskvision.graveyard.entity.UserEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OAuthAccountRepository extends JpaRepository<OAuthAccountEntity, UUID> {
    @EntityGraph(attributePaths = {"user"})
    Optional<OAuthAccountEntity> findByProviderAndProviderUserId(String provider, String providerUserId);
    boolean existsByProviderAndProviderUserId(String provider, String providerUserId);
    List<OAuthAccountEntity> findByUser(UserEntity user);
    Optional<OAuthAccountEntity> findByUserAndProvider(UserEntity user, String provider);
}
