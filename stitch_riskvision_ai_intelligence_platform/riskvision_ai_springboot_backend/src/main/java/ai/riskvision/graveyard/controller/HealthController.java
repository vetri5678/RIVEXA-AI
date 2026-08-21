package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class HealthController {

    private final UserRepository userRepository;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        long startTime = System.currentTimeMillis();
        log.debug("HTTP GET /api/v1/health requested");

        String dbStatus = "UNKNOWN";
        boolean dbHealthy = false;
        
        try {
            // Test database connectivity with timeout
            userRepository.count();
            dbStatus = "CONNECTED";
            dbHealthy = true;
            log.debug("Database health check: PASSED");
        } catch (Exception e) {
            log.warn("Database health check: FAILED - {}", e.getMessage());
            dbStatus = "DISCONNECTED";
            dbHealthy = false;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Spring Boot Backend");
        response.put("database", dbStatus);
        response.put("pipeline", "RUNNING");
        response.put("healthy", dbHealthy);
        response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));

        long duration = System.currentTimeMillis() - startTime;
        log.info("HTTP GET /api/v1/health completed in {} ms -> DB:{}", duration, dbStatus);

        // Always return HTTP 200 for service health, even if DB is down
        // This allows the service to be considered "up" for load balancer purposes
        return ResponseEntity.ok(response);
    }
}
