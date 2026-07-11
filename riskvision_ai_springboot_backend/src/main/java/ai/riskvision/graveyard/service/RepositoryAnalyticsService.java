package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.dto.repository.RepositoryStatisticsResponse;
import ai.riskvision.graveyard.repository.RepositoryEntityRepository;
import ai.riskvision.graveyard.repository.RepositoryPredictionEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepositoryAnalyticsService {

    private final RepositoryEntityRepository repoRepository;
    private final RepositoryPredictionEntityRepository predictionRepository;

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
}
