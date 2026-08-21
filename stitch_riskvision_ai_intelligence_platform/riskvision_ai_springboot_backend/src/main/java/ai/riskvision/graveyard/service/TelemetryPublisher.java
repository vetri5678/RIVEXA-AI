package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.entity.AuditLogEntity;
import ai.riskvision.graveyard.entity.TelemetryMetricsEntity;
import ai.riskvision.graveyard.repository.AuditLogRepository;
import ai.riskvision.graveyard.repository.TelemetryMetricsEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ai.riskvision.graveyard.config.TelemetryWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelemetryPublisher {

    private final TelemetryWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;
    private final TelemetryMetricsEntityRepository telemetryMetricsRepository;
    private final AuditLogRepository auditLogRepository;

    private LocalDateTime lastAuditBroadcast = LocalDateTime.now();

    /**
     * Broadcasts current telemetry snapshot to all WebSocket subscribers every 15 seconds.
     */
    @Scheduled(fixedRate = 15000)
    public void broadcastTelemetry() {
        try {
            Optional<TelemetryMetricsEntity> latestOpt = telemetryMetricsRepository.findTopByOrderByTimestampDesc();
            MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
            long heapUsed = memBean.getHeapMemoryUsage().getUsed();
            long heapMax = memBean.getHeapMemoryUsage().getMax();
            double heapPct = heapMax > 0 ? ((double) heapUsed / heapMax) * 100.0 : 0;
            int threadCount = ManagementFactory.getThreadMXBean().getThreadCount();

            Map<String, Object> payload = new LinkedHashMap<>();
            if (latestOpt.isPresent()) {
                TelemetryMetricsEntity latest = latestOpt.get();
                payload.put("cpu_usage", latest.getCpuUsage() != null ? latest.getCpuUsage() : 0);
                payload.put("memory_usage", latest.getMemoryUsage() != null ? latest.getMemoryUsage() : 0);
                payload.put("heap_usage", Math.round(heapPct * 10.0) / 10.0);
                payload.put("disk_usage", latest.getDiskUsage() != null ? latest.getDiskUsage() : 0);
                payload.put("network_usage", latest.getNetworkUsage() != null ? latest.getNetworkUsage() : 0);
                payload.put("thread_count", threadCount);
                payload.put("active_sessions", latest.getActiveSessions() != null ? latest.getActiveSessions() : 0);
                payload.put("api_latency", latest.getApiLatency() != null ? latest.getApiLatency() : 0);
                payload.put("prediction_latency", latest.getPredictionLatency() != null ? latest.getPredictionLatency() : 0);
            } else {
                payload.put("cpu_usage", 0);
                payload.put("memory_usage", 0);
                payload.put("heap_usage", Math.round(heapPct * 10.0) / 10.0);
                payload.put("disk_usage", 0);
                payload.put("thread_count", threadCount);
                payload.put("active_sessions", 0);
                payload.put("api_latency", 0);
                payload.put("prediction_latency", 0);
            }
            payload.put("timestamp", LocalDateTime.now().toString());

            Map<String, Object> message = new LinkedHashMap<>();
            message.put("type", "TELEMETRY");
            message.put("payload", payload);
            String json = objectMapper.writeValueAsString(message);

            webSocketHandler.broadcast(json);
            log.debug("Broadcast telemetry update via WebSocket");
        } catch (Exception e) {
            log.error("Failed to broadcast telemetry via WebSocket: {}", e.getMessage());
        }
    }

    /**
     * Broadcasts new audit events to all WebSocket subscribers every 5 seconds.
     * Only sends events created since the last broadcast.
     */
    @Scheduled(fixedRate = 5000)
    public void broadcastAuditEvents() {
        try {
            List<AuditLogEntity> recentLogs = auditLogRepository.findTop20ByOrderByCreatedAtDesc();
            List<Map<String, Object>> newEvents = new ArrayList<>();

            for (AuditLogEntity logItem : recentLogs) {
                if (logItem.getCreatedAt() != null && logItem.getCreatedAt().isAfter(lastAuditBroadcast)) {
                    Map<String, Object> event = new LinkedHashMap<>();
                    event.put("id", logItem.getId().toString());
                    event.put("action", logItem.getEventType());
                    event.put("module", logItem.getModule() != null ? logItem.getModule() : "SYSTEM");
                    event.put("severity", logItem.getSeverity() != null ? logItem.getSeverity() : "LOW");
                    event.put("status", logItem.getStatus());
                    event.put("description", logItem.getDetails());
                    event.put("username", logItem.getUsername());
                    event.put("duration_ms", logItem.getDurationMs());
                    event.put("created_at", logItem.getCreatedAt().toString());
                    newEvents.add(event);
                }
            }

            if (!newEvents.isEmpty()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("events", newEvents);
                payload.put("timestamp", LocalDateTime.now().toString());

                Map<String, Object> message = new LinkedHashMap<>();
                message.put("type", "AUDIT");
                message.put("payload", payload);
                String json = objectMapper.writeValueAsString(message);

                webSocketHandler.broadcast(json);
                log.debug("Broadcast {} new audit events via WebSocket", newEvents.size());
            }

            lastAuditBroadcast = LocalDateTime.now();
        } catch (Exception e) {
            log.error("Failed to broadcast audit events via WebSocket: {}", e.getMessage());
        }
    }

    /**
     * Immediately broadcast a single audit event (called when a new event is recorded).
     */
    public void publishAuditEvent(AuditLogEntity entity) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("id", entity.getId().toString());
            event.put("action", entity.getEventType());
            event.put("module", entity.getModule() != null ? entity.getModule() : "SYSTEM");
            event.put("severity", entity.getSeverity() != null ? entity.getSeverity() : "LOW");
            event.put("status", entity.getStatus());
            event.put("description", entity.getDetails());
            event.put("username", entity.getUsername());
            event.put("duration_ms", entity.getDurationMs());
            event.put("created_at", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : LocalDateTime.now().toString());

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("events", List.of(event));
            payload.put("timestamp", LocalDateTime.now().toString());

            Map<String, Object> message = new LinkedHashMap<>();
            message.put("type", "AUDIT");
            message.put("payload", payload);
            String json = objectMapper.writeValueAsString(message);

            webSocketHandler.broadcast(json);
        } catch (Exception e) {
            log.error("Failed to publish immediate audit event via WebSocket: {}", e.getMessage());
        }
    }
}
