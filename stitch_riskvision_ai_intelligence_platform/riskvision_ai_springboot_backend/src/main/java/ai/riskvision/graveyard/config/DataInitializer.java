package ai.riskvision.graveyard.config;

import ai.riskvision.graveyard.entity.*;
import ai.riskvision.graveyard.model.PredictionRecord;
import ai.riskvision.graveyard.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Random;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RepositoryEntityRepository repoRepository;
    private final RepositoryMetricsEntityRepository metricsRepository;
    private final PredictionRecordRepository predictionRecordRepository;
    private final ProjectRepository projectRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final ai.riskvision.graveyard.service.RepoPredictionService predictionService;

    // Injecting new telemetry repositories
    private final ModelPerformanceEntityRepository modelPerformanceRepository;
    private final XAIFeatureImportanceEntityRepository xaiFeatureImportanceRepository;
    private final SystemMetricsEntityRepository systemMetricsRepository;
    private final TelemetryMetricsEntityRepository telemetryMetricsRepository;
    private final RiskMetricsEntityRepository riskMetricsRepository;

    @org.springframework.beans.factory.annotation.Value("${riskvision.report.count:100}")
    private int reportCount;

    @org.springframework.beans.factory.annotation.Value("${app.admin.email:${spring.mail.username:admin@example.com}}")
    private String adminEmailAddress;

    @org.springframework.beans.factory.annotation.Value("${app.admin.password:${ADMIN_PASSWORD:CHANGE_ME_ON_FIRST_LOGIN}}")
    private String adminPassword;

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void run(String... args) throws Exception {
        // Seed default admin user
        String adminEmail = adminEmailAddress;
        if (!userRepository.existsByEmail(adminEmail) && !userRepository.existsByUsername("admin")) {
            if ("CHANGE_ME_ON_FIRST_LOGIN".equals(adminPassword)) {
                log.warn("SECURITY WARNING: Admin password not set via ADMIN_PASSWORD env var. " +
                    "Set ADMIN_PASSWORD in your .env file before starting in production!");
            }
            userRepository.save(UserEntity.builder()
                    .email(adminEmail)
                    .username("admin")
                    .password(passwordEncoder.encode(adminPassword))
                    .fullName("Default Admin")
                    .role("admin")
                    .isVerified(true)
                    .isActive(true)
                    .build());
            log.info("Default admin user {} seeded.", adminEmail);
        }

        // Seed Model Performance
        if (modelPerformanceRepository.count() == 0) {
            modelPerformanceRepository.save(ModelPerformanceEntity.builder()
                    .modelName("RiskVision Random Forest Classifier")
                    .algorithm("Random Forest (scikit-learn)")
                    .accuracy(0.942)
                    .precisionVal(0.931)
                    .recall(0.925)
                    .f1Score(0.928)
                    .rocAuc(0.978)
                    .cvScore(0.939)
                    .datasetVersion("v2.4.1-stable")
                    .timestamp(LocalDateTime.now())
                    .build());
            log.info("Model performance metrics seeded.");
        }

        // Seed XAI Feature Importance
        if (xaiFeatureImportanceRepository.count() == 0) {
            xaiFeatureImportanceRepository.save(XAIFeatureImportanceEntity.builder()
                    .featureName("failed_pull_requests")
                    .displayName("Failed Pull Requests")
                    .avgImpact(0.245)
                    .contributionPct(24.5)
                    .occurrenceCount(150)
                    .direction("increases_risk")
                    .timestamp(LocalDateTime.now())
                    .build());
            xaiFeatureImportanceRepository.save(XAIFeatureImportanceEntity.builder()
                    .featureName("inactive_days")
                    .displayName("Inactive Days Count")
                    .avgImpact(0.198)
                    .contributionPct(19.8)
                    .occurrenceCount(140)
                    .direction("increases_risk")
                    .timestamp(LocalDateTime.now())
                    .build());
            xaiFeatureImportanceRepository.save(XAIFeatureImportanceEntity.builder()
                    .featureName("open_issues")
                    .displayName("Open Issues Count")
                    .avgImpact(0.156)
                    .contributionPct(15.6)
                    .occurrenceCount(120)
                    .direction("increases_risk")
                    .timestamp(LocalDateTime.now())
                    .build());
            xaiFeatureImportanceRepository.save(XAIFeatureImportanceEntity.builder()
                    .featureName("technical_debt")
                    .displayName("Technical Debt Ratio")
                    .avgImpact(0.124)
                    .contributionPct(12.4)
                    .occurrenceCount(110)
                    .direction("increases_risk")
                    .timestamp(LocalDateTime.now())
                    .build());
            xaiFeatureImportanceRepository.save(XAIFeatureImportanceEntity.builder()
                    .featureName("bus_factor")
                    .displayName("Team Bus Factor")
                    .avgImpact(0.102)
                    .contributionPct(10.2)
                    .occurrenceCount(95)
                    .direction("decreases_risk")
                    .timestamp(LocalDateTime.now())
                    .build());
            xaiFeatureImportanceRepository.save(XAIFeatureImportanceEntity.builder()
                    .featureName("code_coverage")
                    .displayName("Unit Test Coverage")
                    .avgImpact(0.095)
                    .contributionPct(9.5)
                    .occurrenceCount(85)
                    .direction("decreases_risk")
                    .timestamp(LocalDateTime.now())
                    .build());
            xaiFeatureImportanceRepository.save(XAIFeatureImportanceEntity.builder()
                    .featureName("velocity")
                    .displayName("Commit Velocity")
                    .avgImpact(0.080)
                    .contributionPct(8.0)
                    .occurrenceCount(70)
                    .direction("decreases_risk")
                    .timestamp(LocalDateTime.now())
                    .build());
            log.info("XAI Feature Importances seeded.");
        }

        Random random = new Random(42);

        // Seed historical telemetry points (last 30 days)
        if (systemMetricsRepository.count() == 0) {
            log.info("Seeding historical telemetry points...");
            for (int d = 30; d >= 0; d--) {
                LocalDateTime time = LocalDateTime.now().minusDays(d);
                double baseRisk = 30.0 + (Math.sin(d * 0.5) * 10.0) + random.nextDouble() * 5.0;

                systemMetricsRepository.save(SystemMetricsEntity.builder()
                        .cpuUsage(10.0 + random.nextDouble() * 15.0)
                        .memoryUsage(42.0 + random.nextDouble() * 8.0)
                        .diskUsage(62.0)
                        .runningThreads(85 + random.nextInt(15))
                        .activeUsers(2 + random.nextInt(4))
                        .dbConnections(5 + random.nextInt(5))
                        .apiResponseTimeMs(15 + (long) random.nextInt(20))
                        .modelInferenceTimeMs(20 + (long) random.nextInt(15))
                        .serverUptimeSeconds(86400L * (30 - d) + 1200)
                        .timestamp(time)
                        .build());

                telemetryMetricsRepository.save(TelemetryMetricsEntity.builder()
                        .commitsCount(100 + random.nextInt(100))
                        .pullRequestsCount(15 + random.nextInt(20))
                        .failedBuildsCount(random.nextInt(4))
                        .successfulBuildsCount(25 + random.nextInt(15))
                        .timestamp(time)
                        .build());

                riskMetricsRepository.save(RiskMetricsEntity.builder()
                        .graveyardIndex(baseRisk)
                        .healthScore(100.0 - baseRisk)
                        .avgFailureProbability(baseRisk / 100.0)
                        .healthyCount(60 + random.nextInt(10))
                        .atRiskCount(25 + random.nextInt(8))
                        .criticalCount(5 + random.nextInt(5))
                        .totalAnalyzed(100)
                        .trend(0.8)
                        .timestamp(time)
                        .build());
            }
            log.info("Historical telemetry points seeded successfully.");
        }

        if (repoRepository.count() > 0) {
            log.info("Database already seeded. Skipping initial data population.");
            return;
        }

        log.info("Starting database seeding for {} reports...", reportCount);

        String[] prefixes = {"nexus", "apex", "cyber", "quantum", "stellar", "alpha", "delta", "spectra", "aurora", "vortex"};
        String[] nouns = {"auth", "gateway", "billing", "analytics", "logger", "cache", "scheduler", "pipeline", "parser", "sync"};
        String[] suffixes = {"service", "manager", "engine", "handler", "agent", "worker", "broker", "proxy", "node", "hub"};
        String[] languages = {"Java", "Python", "Go", "JavaScript", "TypeScript"};
        String[] technologies = {"Spring Boot, Cloud", "FastAPI, PyTorch", "Go, Redis", "Node.js, Express", "React, Webpack"};
        String[] orgs = {"Stitch RiskVision", "Alpha Telemetry", "Spectra Core", "Stitch SecOps"};
        String[] owners = {"vetri5678", "secops-team", "auth-team", "data-ops", "ui-team", "dev-backend"};
        String[] gitProviders = {"GITHUB", "GITLAB"};
        String[] licenses = {"MIT", "Apache-2.0", "GPL-3.0", "Proprietary"};
        String[] stages = {"ACTIVE", "MAINTENANCE", "DEPRECATED"};
        String[] statuses = {"ACTIVE", "INACTIVE"};

        for (int i = 0; i < reportCount; i++) {
            String provider = gitProviders[random.nextInt(gitProviders.length)];
            int langIdx = random.nextInt(languages.length);
            String lang = languages[langIdx];
            String tech = technologies[langIdx];

            String repoName = i == 0 ? "riskprediction-ai-" : (prefixes[random.nextInt(prefixes.length)] + "-" + nouns[random.nextInt(nouns.length)] + "-" + suffixes[random.nextInt(suffixes.length)] + "-" + (i + 1));
            String ownerName = i == 0 ? "vetri5678" : owners[random.nextInt(owners.length)];
            String repoUrl = "https://github.com/" + ownerName + "/" + repoName;

            int contributors = 1 + random.nextInt(29);
            int openIssues = random.nextInt(100);

            RepositoryEntity repo = RepositoryEntity.builder()
                    .repositoryName(repoName)
                    .description("AI-generated telemetry analysis profile for " + repoName + " service.")
                    .organization(orgs[random.nextInt(orgs.length)])
                    .owner(ownerName)
                    .repositoryUrl(repoUrl)
                    .gitProvider(provider)
                    .branch(random.nextBoolean() ? "main" : "develop")
                    .technology(tech)
                    .language(lang)
                    .projectType("Service")
                    .visibility("PRIVATE")
                    .license(licenses[random.nextInt(licenses.length)])
                    .healthScore(50.0)
                    .failureProbability(0.5)
                    .predictionStatus("PENDING")
                    .lifecycleStage(stages[random.nextInt(stages.length)])
                    .status(statuses[random.nextInt(statuses.length)])
                    .riskLevel("MEDIUM")
                    .aiConfidence(0.80)
                    .contributors(contributors)
                    .openIssues(openIssues)
                    .lastCommitDate(LocalDateTime.now().minusDays(random.nextInt(200)))
                    .lastSyncDate(LocalDateTime.now().minusMinutes(30))
                    .build();

            repo = repoRepository.save(repo);

            if (!projectRepository.existsById(repo.getId())) {
                projectRepository.save(ProjectEntity.builder()
                        .id(repo.getId())
                        .externalId(repo.getId().toString())
                        .name(repo.getRepositoryName())
                        .status("active")
                        .build());
            }

            int pullRequests = 10 + random.nextInt(490);
            int merged = (int) (pullRequests * (0.7 + random.nextDouble() * 0.3));

            RepositoryMetricsEntity metrics = RepositoryMetricsEntity.builder()
                    .repositoryId(repo.getId())
                    .commitCount(50 + random.nextInt(4950))
                    .pullRequests(pullRequests)
                    .mergedPullRequests(merged)
                    .failedPullRequests(pullRequests - merged)
                    .contributors(contributors)
                    .activeContributors(1 + random.nextInt(contributors))
                    .inactiveDays(random.nextInt(180))
                    .openIssues(openIssues)
                    .closedIssues(random.nextInt(500))
                    .codeCoverage(10.0 + random.nextDouble() * 89.0)
                    .documentationScore(10.0 + random.nextDouble() * 89.0)
                    .buildSuccessRate(40.0 + random.nextDouble() * 60.0)
                    .cyclomaticComplexity(5.0 + random.nextDouble() * 55.0)
                    .technicalDebt(2.0 + random.nextDouble() * 88.0)
                    .busFactor(1 + random.nextInt(5))
                    .velocity(random.nextDouble() * 25.0)
                    .build();

            metricsRepository.save(metrics);

            RepositoryPredictionEntity pred = predictionService.runPrediction(repo.getId(), "SYSTEM");

            PredictionRecord pr = PredictionRecord.builder()
                    .projectId(repo.getId())
                    .projectName(repo.getRepositoryName())
                    .failureProbability(pred.getFailureProbability())
                    .riskScore(pred.getRiskScore())
                    .riskLevel(pred.getRiskLevel())
                    .confidenceLevel(pred.getConfidence())
                    .commitsToday(random.nextInt(15))
                    .mergedPrs(random.nextInt(5))
                    .openIssues(repo.getOpenIssues())
                    .closedIssues(metrics.getClosedIssues())
                    .failedBuilds(pullRequests - merged)
                    .successfulBuilds(merged)
                    .predictedAt(LocalDateTime.now().minusDays(random.nextInt(7)))
                    .build();

            predictionRecordRepository.save(pr);
        }

        log.info("Database successfully seeded with mock telemetry registers and predictions.");
    }
}
