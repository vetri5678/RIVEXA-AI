package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.dto.repository.RepositoryStatisticsResponse;
import ai.riskvision.graveyard.entity.UserEntity;
import ai.riskvision.graveyard.repository.RepositoryEntityRepository;
import ai.riskvision.graveyard.repository.RepositoryPredictionEntityRepository;
import ai.riskvision.graveyard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepositoryAnalyticsService {

    private final RepositoryEntityRepository repoRepository;
    private final RepositoryPredictionEntityRepository predictionRepository;
    private final UserRepository userRepository;

    /**
     * Global statistics across ALL repositories. Use only for admin views.
     */
    @Transactional(readOnly = true)
    public RepositoryStatisticsResponse computeStatistics() {
        long total = repoRepository.count();
        long archived = repoRepository.countByStatus("ARCHIVED");
        long active = repoRepository.countByStatus("ACTIVE");
        long healthy = repoRepository.countHealthy();
        long underObservation = repoRepository.countUnderObservation();
        long highRisk = repoRepository.countByRiskLevel("HIGH") + repoRepository.countByRiskLevel("CRITICAL");
        long predictedDead = repoRepository.countByPredictionStatus("DEAD");
        long withPredictions = repoRepository.countWithPredictions();

        Double avgHealth = repoRepository.avgHealthScore();
        Double avgFailProb = repoRepository.avgFailureProbability();

        double aiCoverage = total > 0 ? ((double) withPredictions / total) * 100.0 : 0.0;
        long totalPredictions = predictionRepository.count();

        String lastSyncTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        return RepositoryStatisticsResponse.builder()
                .total(total)
                .healthy(healthy)
                .underObservation(underObservation)
                .highRisk(highRisk)
                .predictedDead(predictedDead)
                .archived(archived)
                .active(active)
                .pendingPrediction(repoRepository.countByPredictionStatus("PENDING"))
                .aiCoveragePercent(Math.round(aiCoverage * 10.0) / 10.0)
                .avgHealthScore(avgHealth != null ? Math.round(avgHealth * 10.0) / 10.0 : 0.0)
                .avgFailureProbability(avgFailProb != null ? Math.round(avgFailProb * 1000.0) / 1000.0 : 0.0)
                .totalPredictionsRun(totalPredictions)
                .lastSyncTime(lastSyncTime)
                .build();
    }

    /**
     * Per-user statistics — scoped strictly to repositories owned by the given user.
     * Returns zeroed stats when the user has no repositories (e.g. GitHub not connected).
     */
    @Transactional(readOnly = true)
    public RepositoryStatisticsResponse computeStatisticsForUser(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return emptyStats();
        }

        Optional<UserEntity> userOpt = userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByUsername(identifier))
                .or(() -> {
                    try {
                        return userRepository.findById(UUID.fromString(identifier));
                    } catch (Exception e) {
                        return Optional.empty();
                    }
                });
        if (userOpt.isEmpty()) return emptyStats();

        UUID userId = userOpt.get().getId();
        long total = repoRepository.countByUserId(userId);
        if (total == 0) return emptyStats();

        long active = repoRepository.countByUserIdAndStatus(userId, "ACTIVE");
        long archived = repoRepository.countByUserIdAndStatus(userId, "ARCHIVED");
        long healthy = repoRepository.countHealthyByUserId(userId);
        long underObservation = repoRepository.countUnderObservationByUserId(userId);
        long highRisk = repoRepository.countByUserIdAndRiskLevelIgnoreCase(userId, "HIGH")
                      + repoRepository.countByUserIdAndRiskLevelIgnoreCase(userId, "CRITICAL");
        long predictedDead = repoRepository.countPredictedDeadByUserId(userId);
        long withPredictions = repoRepository.countByUserIdWithPredictions(userId);

        Double avgFailProb = repoRepository.avgFailureProbabilityByUserId(userId);
        if (avgFailProb == null) avgFailProb = 0.0;

        Double avgHealth = repoRepository.avgHealthScoreByUserId(userId);
        if (avgHealth == null || avgHealth == 0.0) {
            avgHealth = Math.max(0.0, (1.0 - avgFailProb) * 100.0);
        }

        double aiCoverage = total > 0 ? ((double) withPredictions / total) * 100.0 : 0.0;
        long totalPredictions = predictionRepository.countByUserId(userId);

        String lastSyncTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        return RepositoryStatisticsResponse.builder()
                .total(total)
                .healthy(healthy)
                .underObservation(underObservation)
                .highRisk(highRisk)
                .predictedDead(predictedDead)
                .archived(archived)
                .active(active)
                .pendingPrediction(total - withPredictions)
                .aiCoveragePercent(Math.round(aiCoverage * 10.0) / 10.0)
                .avgHealthScore(Math.round(avgHealth * 10.0) / 10.0)
                .avgFailureProbability(Math.round(avgFailProb * 1000.0) / 1000.0)
                .totalPredictionsRun(totalPredictions)
                .lastSyncTime(lastSyncTime)
                .build();
    }

    private RepositoryStatisticsResponse emptyStats() {
        return RepositoryStatisticsResponse.builder()
                .total(0L).healthy(0L).underObservation(0L).highRisk(0L)
                .predictedDead(0L).archived(0L).active(0L).pendingPrediction(0L)
                .aiCoveragePercent(0.0).avgHealthScore(0.0).avgFailureProbability(0.0)
                .totalPredictionsRun(0L)
                .lastSyncTime(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build();
    }
}
