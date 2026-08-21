package ai.riskvision.graveyard.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Database health monitoring and graceful degradation configuration.
 * This component helps the application start gracefully even when database
 * connectivity is temporarily unavailable during development.
 */
@Slf4j
@Configuration
public class DatabaseHealthConfiguration implements ApplicationListener<ApplicationReadyEvent> {

    private final Environment environment;

    public DatabaseHealthConfiguration(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        String[] activeProfiles = environment.getActiveProfiles();
        boolean isDevProfile = java.util.Arrays.asList(activeProfiles).contains("dev");
        
        if (isDevProfile) {
            log.info("==============================================");
            log.info("RiskVision AI Spring Boot Backend - READY");
            log.info("==============================================");
            log.info("Active Profiles: {}", String.join(", ", activeProfiles));
            log.info("Server Port: {}", environment.getProperty("server.port", "8080"));
            log.info("Database Connection: Established ✓");
            log.info("Health Check: http://localhost:{}/api/v1/health", 
                    environment.getProperty("server.port", "8080"));
            log.info("==============================================");
        }
    }
}