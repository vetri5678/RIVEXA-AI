package ai.riskvision.graveyard.config;

import ai.riskvision.graveyard.entity.*;
import ai.riskvision.graveyard.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private final ModelPerformanceEntityRepository modelPerformanceRepository;
    private final XAIFeatureImportanceEntityRepository xaiFeatureImportanceRepository;
    private final SystemMetricsEntityRepository systemMetricsRepository;
    private final TelemetryMetricsEntityRepository telemetryMetricsRepository;
    private final RiskMetricsEntityRepository riskMetricsRepository;

    @org.springframework.beans.factory.annotation.Value("${app.admin.email:${spring.mail.username:admin@example.com}}")
    private String adminEmailAddress;

    @org.springframework.beans.factory.annotation.Value("${app.admin.password:${ADMIN_PASSWORD:CHANGE_ME_ON_FIRST_LOGIN}}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        log.info("Initializing RiskVision AI system data...");
        try {
            initializeData();
        } catch (Exception e) {
            log.warn("DataInitializer could not complete (DB may not be reachable yet): {}. The application will continue.", e.getMessage());
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public void initializeData() {

        // Ensure default ADMIN user exists
        String targetEmail = (adminEmailAddress != null && !adminEmailAddress.trim().isEmpty())
                ? adminEmailAddress.trim().toLowerCase()
                : "admin@example.com";

        String initialPass = (adminPassword != null && !adminPassword.trim().isEmpty()) ? adminPassword.trim() : "AdminPass123!";
        UserEntity adminUser = userRepository.findByEmail(targetEmail).orElseGet(() ->
            UserEntity.builder()
                .username("admin")
                .email(targetEmail)
                .fullName("System Administrator")
                .role("ADMIN")
                .build()
        );
        adminUser.setPassword(passwordEncoder.encode(initialPass));
        adminUser.setIsVerified(true);
        adminUser.setIsActive(true);
        adminUser.setFailedLoginAttempts(0);
        adminUser.setLockedUntil(null);
        userRepository.save(adminUser);
        log.info("Initialized/reset default ADMIN user account ({})", targetEmail);

        // Initialize Model Performance Metrics if empty
        if (modelPerformanceRepository.count() == 0) {
            ModelPerformanceEntity activeModel = ModelPerformanceEntity.builder()
                    .modelName("XGBoost Classifier")
                    .algorithm("XGBoost")
                    .accuracy(0.9313)
                    .precisionVal(0.9254)
                    .recall(0.9313)
                    .f1Score(0.9274)
                    .rocAuc(0.9632)
                    .cvScore(0.9285)
                    .timestamp(LocalDateTime.now())
                    .datasetVersion("xgboost-v1.0")
                    .build();
            modelPerformanceRepository.save(activeModel);

            log.info("Initialized default model performance metrics for XGBoost.");
        }

        // Initialize XAI Feature Importance if empty
        if (xaiFeatureImportanceRepository.count() == 0) {
            Object[][] features = {
                    {"bus_factor", "Bus Factor Vulnerability", 0.38, 28.5, 45, "INCREASES_RISK"},
                    {"commit_frequency", "Commit Frequency Decay", 0.29, 21.4, 38, "INCREASES_RISK"},
                    {"contributor_churn", "Contributor Churn Rate", 0.22, 16.8, 32, "INCREASES_RISK"},
                    {"open_issue_stagnation", "Issue Stagnation Duration", 0.18, 13.2, 29, "INCREASES_RISK"},
                    {"test_coverage", "Test Suite Coverage", -0.25, 18.6, 36, "DECREASES_RISK"},
                    {"code_documentation_ratio", "Documentation Ratio", -0.15, 11.2, 22, "DECREASES_RISK"},
                    {"build_success_rate", "CI/CD Build Health", -0.31, 23.1, 41, "DECREASES_RISK"}
            };

            for (Object[] f : features) {
                XAIFeatureImportanceEntity entity = XAIFeatureImportanceEntity.builder()
                        .featureName((String) f[0])
                        .displayName((String) f[1])
                        .avgImpact((Double) f[2])
                        .contributionPct((Double) f[3])
                        .occurrenceCount((Integer) f[4])
                        .direction((String) f[5])
                        .timestamp(LocalDateTime.now())
                        .build();
                xaiFeatureImportanceRepository.save(entity);
            }
            log.info("Initialized default XAI feature importance weights.");
        }

        // Initialize System Metrics if empty
        if (systemMetricsRepository.count() == 0) {
            SystemMetricsEntity sysMetrics = SystemMetricsEntity.builder()
                    .timestamp(LocalDateTime.now())
                    .cpuUsage(18.4)
                    .memoryUsage(42.1)
                    .diskUsage(28.7)
                    .apiResponseTimeMs(45L)
                    .modelInferenceTimeMs(128L)
                    .activeUsers(1)
                    .build();
            systemMetricsRepository.save(sysMetrics);
            log.info("Initialized initial system metrics snapshot.");
        }

        // Initialize Telemetry Metrics if empty
        if (telemetryMetricsRepository.count() == 0) {
            TelemetryMetricsEntity telemetry = TelemetryMetricsEntity.builder()
                    .timestamp(LocalDateTime.now())
                    .cpuUsage(18.4)
                    .memoryUsage(42.1)
                    .diskUsage(28.7)
                    .activeSessions(1)
                    .threadCount(12)
                    .commitsCount(15)
                    .pullRequestsCount(5)
                    .build();
            telemetryMetricsRepository.save(telemetry);
            log.info("Initialized telemetry metrics snapshot.");
        }

        // Initialize Risk Metrics if empty
        if (riskMetricsRepository.count() == 0) {
            RiskMetricsEntity riskSummary = RiskMetricsEntity.builder()
                    .timestamp(LocalDateTime.now())
                    .totalAnalyzed(0)
                    .graveyardIndex(0.0)
                    .criticalCount(0)
                    .healthyCount(0)
                    .atRiskCount(0)
                    .build();
            riskMetricsRepository.save(riskSummary);
            log.info("Initialized risk metrics summary.");
        }

        log.info("DataInitializer completed cleanly.");
    }
}
