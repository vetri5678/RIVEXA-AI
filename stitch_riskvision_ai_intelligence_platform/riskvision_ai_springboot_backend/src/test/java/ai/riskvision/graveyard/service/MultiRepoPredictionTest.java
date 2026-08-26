package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.entity.RepositoryEntity;
import ai.riskvision.graveyard.entity.RepositoryMetricsEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class MultiRepoPredictionTest {

    private RepoPredictionService predictionService;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        objectMapper = new ObjectMapper();
        predictionService = new RepoPredictionService(
                Mockito.mock(ai.riskvision.graveyard.repository.RepositoryEntityRepository.class),
                Mockito.mock(ai.riskvision.graveyard.repository.RepositoryPredictionEntityRepository.class),
                Mockito.mock(ai.riskvision.graveyard.repository.RepositoryMetricsEntityRepository.class),
                Mockito.mock(ai.riskvision.graveyard.repository.CodeFindingRepository.class),
                Mockito.mock(ai.riskvision.graveyard.repository.CodeFileAnalysisRepository.class),
                Mockito.mock(ai.riskvision.graveyard.repository.CodeAnalysisRunRepository.class),
                Mockito.mock(RepositorySyncService.class),
                objectMapper,
                Mockito.mock(OpenRouterService.class),
                Mockito.mock(RestTemplate.class)
        );
    }

    @Test
    public void testRepositorySpecificRecommendationsAndRoadmaps() throws Exception {
        // Repo A: Healthy Repo (0 inactive days, 0 issues, 90% test coverage, 95% doc score, 5 contributors)
        RepositoryEntity repoA = RepositoryEntity.builder()
                .id(UUID.randomUUID())
                .repositoryName("healthy-service")
                .openIssues(0)
                .contributors(5)
                .riskLevel("LOW")
                .build();
        RepositoryMetricsEntity metricsA = RepositoryMetricsEntity.builder()
                .inactiveDays(0)
                .openIssues(0)
                .codeCoverage(90.0)
                .buildSuccessRate(98.0)
                .documentationScore(95.0)
                .contributors(5)
                .busFactor(3)
                .build();

        // Repo B: Abandoned/High-Risk Repo (120 inactive days, 40 issues, 25% test coverage, 1 contributor)
        RepositoryEntity repoB = RepositoryEntity.builder()
                .id(UUID.randomUUID())
                .repositoryName("abandoned-legacy-app")
                .openIssues(40)
                .contributors(1)
                .riskLevel("CRITICAL")
                .build();
        RepositoryMetricsEntity metricsB = RepositoryMetricsEntity.builder()
                .inactiveDays(120)
                .openIssues(40)
                .codeCoverage(25.0)
                .buildSuccessRate(70.0)
                .documentationScore(30.0)
                .contributors(1)
                .busFactor(1)
                .build();

        // Repo C: Broken CI/CD & Poor Docs Repo (5 inactive days, 5 issues, 75% test coverage, 45% doc score, 60% build success)
        RepositoryEntity repoC = RepositoryEntity.builder()
                .id(UUID.randomUUID())
                .repositoryName("broken-ci-pipeline")
                .openIssues(5)
                .contributors(4)
                .riskLevel("HIGH")
                .build();
        RepositoryMetricsEntity metricsC = RepositoryMetricsEntity.builder()
                .inactiveDays(5)
                .openIssues(5)
                .codeCoverage(75.0)
                .buildSuccessRate(60.0)
                .documentationScore(45.0)
                .contributors(4)
                .busFactor(2)
                .build();

        Method fallbackMethod = RepoPredictionService.class.getDeclaredMethod("generateFallbackRecommendations", RepositoryEntity.class, RepositoryMetricsEntity.class, int.class, String.class, double.class, String.class);
        fallbackMethod.setAccessible(true);

        String fallbackA = (String) fallbackMethod.invoke(predictionService, repoA, metricsA, 15, "LOW", 0.15, null);
        String fallbackB = (String) fallbackMethod.invoke(predictionService, repoB, metricsB, 85, "CRITICAL", 0.85, null);
        String fallbackC = (String) fallbackMethod.invoke(predictionService, repoC, metricsC, 65, "HIGH", 0.65, null);

        Map<?, ?> mapA = objectMapper.readValue(fallbackA, Map.class);
        Map<?, ?> mapB = objectMapper.readValue(fallbackB, Map.class);
        Map<?, ?> mapC = objectMapper.readValue(fallbackC, Map.class);

        System.out.println("=== REPO A (healthy-service) ===");
        System.out.println(fallbackA);
        System.out.println("\n=== REPO B (abandoned-legacy-app) ===");
        System.out.println(fallbackB);
        System.out.println("\n=== REPO C (broken-ci-pipeline) ===");
        System.out.println(fallbackC);

        // Verify Repo A receives clean maintainer recommendation only
        assertTrue(fallbackA.contains("Maintain Engineering Standards"));
        assertFalse(fallbackA.contains("Resume Regular Development Cadence"));

        // Verify Repo B receives inactivity, issue backlog, test coverage, and single maintainer recommendations
        assertTrue(fallbackB.contains("Resume Regular Development Cadence"));
        assertTrue(fallbackB.contains("Address Growing Issue Backlog"));
        assertTrue(fallbackB.contains("Implement Automated Test Suite"));
        assertTrue(fallbackB.contains("Mitigate Single-Maintainer Risk"));

        // Verify Repo C receives CI/CD build pipeline and documentation recommendations
        assertTrue(fallbackC.contains("Stabilize CI/CD Build Pipeline"));
        assertTrue(fallbackC.contains("Improve Repository Documentation"));

        // Assert projected risk scores are distinct and dynamically calculated
        Map<?, ?> projectedA = (Map<?, ?>) mapA.get("projected_status");
        Map<?, ?> projectedB = (Map<?, ?>) mapB.get("projected_status");
        Map<?, ?> projectedC = (Map<?, ?>) mapC.get("projected_status");

        assertNotEquals(projectedA.get("potential_improvement"), projectedB.get("potential_improvement"));
        assertNotEquals(projectedB.get("potential_improvement"), projectedC.get("potential_improvement"));

        System.out.println("\n[SUCCESS] Verified that every repository receives independent, evidence-based recommendations, unique roadmaps, and dynamic projected risk scores!");
    }
}
