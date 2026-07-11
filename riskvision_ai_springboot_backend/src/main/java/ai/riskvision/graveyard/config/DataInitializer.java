package ai.riskvision.graveyard.config;

import ai.riskvision.graveyard.entity.RepositoryEntity;
import ai.riskvision.graveyard.entity.RepositoryMetricsEntity;
import ai.riskvision.graveyard.model.PredictionRecord;
import ai.riskvision.graveyard.repository.PredictionRecordRepository;
import ai.riskvision.graveyard.repository.RepositoryEntityRepository;
import ai.riskvision.graveyard.repository.RepositoryMetricsEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RepositoryEntityRepository repoRepository;
    private final RepositoryMetricsEntityRepository metricsRepository;
    private final PredictionRecordRepository predictionRecordRepository;

    @Override
    public void run(String... args) throws Exception {
        if (repoRepository.count() > 0) {
            log.info("Database already seeded. Skipping initial data population.");
            return;
        }

        log.info("Starting database seeding for RiskVision AI...");

        // 1. Seed Repository 1: Low Risk (Nominal)
        RepositoryEntity repo1 = RepositoryEntity.builder()
                .repositoryName("riskvision-api-gateway")
                .description("Central API Gateway routing and OAuth2 authorization filter node.")
                .organization("Stitch RiskVision")
                .owner("secops-team")
                .repositoryUrl("https://github.com/stitch-riskvision/api-gateway")
                .gitProvider("GITHUB")
                .branch("main")
                .technology("Java, Spring Cloud Gateway")
                .language("Java")
                .projectType("Microservice")
                .visibility("PRIVATE")
                .license("MIT")
                .healthScore(94.2)
                .failureProbability(0.058)
                .predictionStatus("COMPLETED")
                .lifecycleStage("ACTIVE")
                .status("ACTIVE")
                .riskLevel("LOW")
                .aiConfidence(0.96)
                .contributors(8)
                .openIssues(2)
                .lastCommitDate(LocalDateTime.now().minusHours(4))
                .lastSyncDate(LocalDateTime.now().minusMinutes(30))
                .build();
        repo1 = repoRepository.save(repo1);

        metricsRepository.save(RepositoryMetricsEntity.builder()
                .repositoryId(repo1.getId())
                .commitCount(1280)
                .pullRequests(142)
                .mergedPullRequests(138)
                .failedPullRequests(4)
                .contributors(8)
                .activeContributors(6)
                .inactiveDays(0)
                .openIssues(2)
                .closedIssues(95)
                .codeCoverage(88.5)
                .documentationScore(92.0)
                .buildSuccessRate(98.2)
                .cyclomaticComplexity(12.4)
                .technicalDebt(5.0)
                .busFactor(3)
                .velocity(14.5)
                .build());

        // 2. Seed Repository 2: Medium Risk (Warning)
        RepositoryEntity repo2 = RepositoryEntity.builder()
                .repositoryName("auth-session-manager")
                .description("Distributed cache manager for user session tokens and telemetry state.")
                .organization("Stitch RiskVision")
                .owner("auth-team")
                .repositoryUrl("https://github.com/stitch-riskvision/auth-session-manager")
                .gitProvider("GITHUB")
                .branch("develop")
                .technology("Go, Redis")
                .language("Go")
                .projectType("Service")
                .visibility("PRIVATE")
                .license("MIT")
                .healthScore(62.8)
                .failureProbability(0.372)
                .predictionStatus("COMPLETED")
                .lifecycleStage("ACTIVE")
                .status("ACTIVE")
                .riskLevel("MEDIUM")
                .aiConfidence(0.89)
                .contributors(3)
                .openIssues(18)
                .lastCommitDate(LocalDateTime.now().minusDays(5))
                .lastSyncDate(LocalDateTime.now().minusMinutes(30))
                .build();
        repo2 = repoRepository.save(repo2);

        metricsRepository.save(RepositoryMetricsEntity.builder()
                .repositoryId(repo2.getId())
                .commitCount(410)
                .pullRequests(52)
                .mergedPullRequests(45)
                .failedPullRequests(7)
                .contributors(3)
                .activeContributors(2)
                .inactiveDays(5)
                .openIssues(18)
                .closedIssues(32)
                .codeCoverage(64.2)
                .documentationScore(70.0)
                .buildSuccessRate(85.0)
                .cyclomaticComplexity(24.8)
                .technicalDebt(15.0)
                .busFactor(1)
                .velocity(8.2)
                .build());

        // 3. Seed Repository 3: High Risk (Critical Pending)
        RepositoryEntity repo3 = RepositoryEntity.builder()
                .repositoryName("legacy-audit-extractor")
                .description("Batch job pipeline for extracting audit records from obsolete database instances.")
                .organization("Stitch RiskVision")
                .owner("data-ops")
                .repositoryUrl("https://github.com/stitch-riskvision/legacy-audit-extractor")
                .gitProvider("GITLAB")
                .branch("master")
                .technology("Python, Celery")
                .language("Python")
                .projectType("Job Pipeline")
                .visibility("PRIVATE")
                .license("Proprietary")
                .healthScore(28.4)
                .failureProbability(0.716)
                .predictionStatus("COMPLETED")
                .lifecycleStage("MAINTENANCE")
                .status("ACTIVE")
                .riskLevel("HIGH")
                .aiConfidence(0.92)
                .contributors(1)
                .openIssues(45)
                .lastCommitDate(LocalDateTime.now().minusDays(45))
                .lastSyncDate(LocalDateTime.now().minusMinutes(30))
                .build();
        repo3 = repoRepository.save(repo3);

        metricsRepository.save(RepositoryMetricsEntity.builder()
                .repositoryId(repo3.getId())
                .commitCount(180)
                .pullRequests(19)
                .mergedPullRequests(12)
                .failedPullRequests(7)
                .contributors(1)
                .activeContributors(1)
                .inactiveDays(45)
                .openIssues(45)
                .closedIssues(12)
                .codeCoverage(35.0)
                .documentationScore(40.0)
                .buildSuccessRate(55.0)
                .cyclomaticComplexity(38.2)
                .technicalDebt(35.0)
                .busFactor(1)
                .velocity(1.5)
                .build());

        // 4. Seed Repository 4: Critical Failure Risk (Graveyard Threshold)
        RepositoryEntity repo4 = RepositoryEntity.builder()
                .repositoryName("riskvision-web-frontend")
                .description("V0 alpha web portal frontend for internal ops. Abandoned react branch.")
                .organization("Stitch RiskVision")
                .owner("ui-team")
                .repositoryUrl("https://github.com/stitch-riskvision/riskvision-web-frontend")
                .gitProvider("GITHUB")
                .branch("legacy-v0")
                .technology("JavaScript, React, Webpack")
                .language("JavaScript")
                .projectType("Frontend")
                .visibility("PRIVATE")
                .license("MIT")
                .healthScore(8.5)
                .failureProbability(0.915)
                .predictionStatus("COMPLETED")
                .lifecycleStage("DEPRECATED")
                .status("ACTIVE")
                .riskLevel("CRITICAL")
                .aiConfidence(0.95)
                .contributors(1)
                .openIssues(89)
                .lastCommitDate(LocalDateTime.now().minusDays(180))
                .lastSyncDate(LocalDateTime.now().minusMinutes(30))
                .build();
        repo4 = repoRepository.save(repo4);

        metricsRepository.save(RepositoryMetricsEntity.builder()
                .repositoryId(repo4.getId())
                .commitCount(3450)
                .pullRequests(480)
                .mergedPullRequests(465)
                .failedPullRequests(15)
                .contributors(1)
                .activeContributors(0)
                .inactiveDays(180)
                .openIssues(89)
                .closedIssues(0)
                .codeCoverage(12.4)
                .documentationScore(15.0)
                .buildSuccessRate(10.0)
                .cyclomaticComplexity(55.0)
                .technicalDebt(80.0)
                .busFactor(1)
                .velocity(0.0)
                .build());

        // 5. Seed PredictionRecords for the Dashboard Overview and high-risk listings
        PredictionRecord pr1 = PredictionRecord.builder()
                .projectId(repo1.getId().toString())
                .projectName(repo1.getRepositoryName())
                .failureProbability(repo1.getFailureProbability())
                .riskScore(repo1.getFailureProbability() >= 0.0 ? (int)(repo1.getFailureProbability() * 100) : 0)
                .riskLevel(repo1.getRiskLevel())
                .confidenceLevel(repo1.getAiConfidence())
                .commitsToday(12)
                .mergedPrs(4)
                .openIssues(repo1.getOpenIssues())
                .closedIssues(95)
                .failedBuilds(0)
                .successfulBuilds(24)
                .predictedAt(LocalDateTime.now())
                .build();

        PredictionRecord pr2 = PredictionRecord.builder()
                .projectId(repo2.getId().toString())
                .projectName(repo2.getRepositoryName())
                .failureProbability(repo2.getFailureProbability())
                .riskScore(repo2.getFailureProbability() >= 0.0 ? (int)(repo2.getFailureProbability() * 100) : 0)
                .riskLevel(repo2.getRiskLevel())
                .confidenceLevel(repo2.getAiConfidence())
                .commitsToday(2)
                .mergedPrs(1)
                .openIssues(repo2.getOpenIssues())
                .closedIssues(32)
                .failedBuilds(3)
                .successfulBuilds(18)
                .predictedAt(LocalDateTime.now())
                .build();

        PredictionRecord pr3 = PredictionRecord.builder()
                .projectId(repo3.getId().toString())
                .projectName(repo3.getRepositoryName())
                .failureProbability(repo3.getFailureProbability())
                .riskScore(repo3.getFailureProbability() >= 0.0 ? (int)(repo3.getFailureProbability() * 100) : 0)
                .riskLevel(repo3.getRiskLevel())
                .confidenceLevel(repo3.getAiConfidence())
                .commitsToday(0)
                .mergedPrs(0)
                .openIssues(repo3.getOpenIssues())
                .closedIssues(12)
                .failedBuilds(8)
                .successfulBuilds(4)
                .predictedAt(LocalDateTime.now().minusDays(1))
                .build();

        PredictionRecord pr4 = PredictionRecord.builder()
                .projectId(repo4.getId().toString())
                .projectName(repo4.getRepositoryName())
                .failureProbability(repo4.getFailureProbability())
                .riskScore(repo4.getFailureProbability() >= 0.0 ? (int)(repo4.getFailureProbability() * 100) : 0)
                .riskLevel(repo4.getRiskLevel())
                .confidenceLevel(repo4.getAiConfidence())
                .commitsToday(0)
                .mergedPrs(0)
                .openIssues(repo4.getOpenIssues())
                .closedIssues(0)
                .failedBuilds(15)
                .successfulBuilds(0)
                .predictedAt(LocalDateTime.now().minusDays(3))
                .build();

        predictionRecordRepository.saveAll(List.of(pr1, pr2, pr3, pr4));

        log.info("Database successfully seeded with 4 mock repository profiles and metrics.");
    }
}
