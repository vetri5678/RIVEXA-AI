package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.dto.DashboardOverviewDTO;
import ai.riskvision.graveyard.model.PredictionRecord;
import ai.riskvision.graveyard.repository.PredictionRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DashboardService {

    @Autowired
    private PredictionRecordRepository repository;

    public DashboardOverviewDTO getOverview() {
        long totalProjects = repository.count();
        double avgFailProb = repository.getAverageFailureProbability() != null ? repository.getAverageFailureProbability() : 0.0;
        long criticalCount = repository.countCriticalProjects();

        double healthScore = (1.0 - avgFailProb) * 100.0;
        double graveyardIndex = avgFailProb * 100.0;

        return DashboardOverviewDTO.builder()
                .totalProjects(totalProjects)
                .totalPredictions(totalProjects)
                .predictionsToday(5L)
                .criticalProjects(criticalCount)
                .avgConfidence(0.97)
                .graveyardIndex(Math.round(graveyardIndex * 10.0) / 10.0)
                .healthScore(Math.round(healthScore * 10.0) / 10.0)
                .build();
    }

    public List<PredictionRecord> getHighRiskProjects() {
        return repository.findTop10ByOrderByFailureProbabilityDesc();
    }
}
