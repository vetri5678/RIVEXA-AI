package ai.riskvision.graveyard.repository;

import ai.riskvision.graveyard.entity.LoginHistoryEntity;
import ai.riskvision.graveyard.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LoginHistoryRepository extends JpaRepository<LoginHistoryEntity, UUID> {
    List<LoginHistoryEntity> findByUserOrderByCreatedAtDesc(UserEntity user);
    List<LoginHistoryEntity> findByEmailOrderByCreatedAtDesc(String email);
}
