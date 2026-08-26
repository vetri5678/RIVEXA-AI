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
    Optional<OAuthAccountEntity> findFirstByProviderAndProviderUserId(String provider, String providerUserId);
    default Optional<OAuthAccountEntity> findByProviderAndProviderUserId(String provider, String providerUserId) {
        return findFirstByProviderAndProviderUserId(provider, providerUserId);
    }
    boolean existsByProviderAndProviderUserId(String provider, String providerUserId);
    List<OAuthAccountEntity> findByUser(UserEntity user);
    Optional<OAuthAccountEntity> findFirstByUserAndProvider(UserEntity user, String provider);
    default Optional<OAuthAccountEntity> findByUserAndProvider(UserEntity user, String provider) {
        return findFirstByUserAndProvider(user, provider);
    }
}
