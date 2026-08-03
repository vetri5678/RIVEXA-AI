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

        String dbStatus = "CONNECTED";
        try {
            userRepository.count();
        } catch (Exception e) {
            log.error("Database ping failed during health check: {}", e.getMessage());
            dbStatus = "DISCONNECTED";
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("database", dbStatus);
        response.put("pipeline", "RUNNING");
        response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));

        long duration = System.currentTimeMillis() - startTime;
        log.info("HTTP GET /api/v1/health completed in {} ms -> {}", duration, dbStatus);

        return ResponseEntity.ok(response);
    }
}
