package ai.riskvision.graveyard.scheduler;

import ai.riskvision.graveyard.repository.RefreshTokenRepository;
import ai.riskvision.graveyard.repository.VerificationTokenRepository;
import ai.riskvision.graveyard.service.RepositorySyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledTasks {

    private final RefreshTokenRepository refreshTokenRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final RepositorySyncService repositorySyncService;

    /**
     * Daily cleanup of expired refresh tokens and verification tokens at 2:00 AM.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("Executing daily scheduled cleanup of expired tokens...");
        try {
            refreshTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
            verificationTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
            log.info("Daily expired token cleanup complete.");
        } catch (Exception e) {
            log.error("Error during scheduled token cleanup", e);
        }
    }

    /**
     * Periodic background synchronization of monitored repository telemetry (Hourly).
     */
    @Scheduled(fixedRate = 3600000)
    public void syncRepositoryTelemetry() {
        log.info("Triggering scheduled background repository telemetry sync...");
        try {
            repositorySyncService.syncAllRepositories();
            log.info("Scheduled background repository sync complete.");
        } catch (Exception e) {
            log.error("Error during background repository sync", e);
        }
    }
}
