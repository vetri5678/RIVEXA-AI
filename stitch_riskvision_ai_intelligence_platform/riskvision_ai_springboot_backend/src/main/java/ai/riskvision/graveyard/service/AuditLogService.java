package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.entity.AuditLogEntity;
import ai.riskvision.graveyard.entity.UserEntity;
import ai.riskvision.graveyard.repository.AuditLogRepository;
import ai.riskvision.graveyard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    /**
     * Records a new audit event into the database.
     */
    public AuditLogEntity recordEvent(String eventType, String module, String severity,
                                       String status, String description, String username,
                                       String ipAddress, String endpoint, String httpMethod,
                                       Integer responseCode, Long durationMs, String metadata) {
        UserEntity user = null;
        if (username != null) {
            user = userRepository.findByEmail(username).orElse(null);
        }

        AuditLogEntity entity = AuditLogEntity.builder()
                .eventType(eventType)
                .eventTypeCompat(eventType)
                .module(module != null ? module : "SYSTEM")
                .severity(severity != null ? severity : "LOW")
                .status(status != null ? status : "success")
                .details(description)
                .username(username)
                .ipAddress(ipAddress)
                .endpoint(endpoint)
                .httpMethod(httpMethod)
                .responseCode(responseCode)
                .durationMs(durationMs)
                .metadata(metadata)
                .user(user)
                .build();

        AuditLogEntity saved = auditLogRepository.save(entity);
        log.info("Audit event recorded: type={}, module={}, severity={}, user={}", eventType, module, severity, username);
        return saved;
    }

    /**
     * Simplified audit recording for common events.
     */
    public AuditLogEntity recordEvent(String eventType, String module, String severity,
                                       String description, String username, String ipAddress) {
        return recordEvent(eventType, module, severity, "success", description, username, ipAddress,
                null, null, null, null, null);
    }

    /**
     * Records a failed event.
     */
    public AuditLogEntity recordFailedEvent(String eventType, String module, String severity,
                                             String description, String username, String ipAddress) {
        return recordEvent(eventType, module, severity, "failed", description, username, ipAddress,
                null, null, null, null, null);
    }

    /**
     * Retrieves paginated audit logs, newest first.
     */
    public Map<String, Object> getAuditLogs(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLogEntity> logPage = auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);

        List<Map<String, Object>> items = logPage.getContent().stream()
                .map(this::entityToMap)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", items);
        response.put("total", logPage.getTotalElements());
        response.put("page", page);
        response.put("page_size", size);
        response.put("total_pages", logPage.getTotalPages());
        return response;
    }

    /**
     * Retrieves audit log statistics: event type counts, severity distribution, module distribution.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> response = new LinkedHashMap<>();

        long totalLogs = auditLogRepository.count();
        long last24h = auditLogRepository.countSince(LocalDateTime.now().minusHours(24));
        long lastHour = auditLogRepository.countSince(LocalDateTime.now().minusHours(1));

        response.put("total_events", totalLogs);
        response.put("events_last_24h", last24h);
        response.put("events_last_hour", lastHour);

        List<Object[]> eventTypeCounts = auditLogRepository.countByEventType();
        List<Map<String, Object>> byType = new ArrayList<>();
        for (Object[] row : eventTypeCounts) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("event_type", row[0]);
            item.put("count", row[1]);
            byType.add(item);
        }
        response.put("by_event_type", byType);

        List<Object[]> severityCounts = auditLogRepository.countBySeverity();
        Map<String, Long> bySeverity = new LinkedHashMap<>();
        for (Object[] row : severityCounts) {
            bySeverity.put((String) row[0], (Long) row[1]);
        }
        response.put("by_severity", bySeverity);

        List<Object[]> moduleCounts = auditLogRepository.countByModule();
        Map<String, Long> byModule = new LinkedHashMap<>();
        for (Object[] row : moduleCounts) {
            byModule.put((String) row[0], (Long) row[1]);
        }
        response.put("by_module", byModule);

        return response;
    }

    /**
     * Returns the most recent audit events (for live feed).
     */
    public Map<String, Object> getRecentLive(int limit) {
        List<AuditLogEntity> logs = auditLogRepository.findTop20ByOrderByCreatedAtDesc();
        List<Map<String, Object>> items = logs.stream()
                .limit(limit)
                .map(this::entityToMap)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", items);
        response.put("total", items.size());
        return response;
    }

    /**
     * Filtered search for audit logs.
     */
    public Map<String, Object> filterLogs(String severity, String module, String eventType,
                                            String status, String startDate, String endDate,
                                            int page, int size) {
        LocalDateTime start = startDate != null ? LocalDateTime.parse(startDate) : null;
        LocalDateTime end = endDate != null ? LocalDateTime.parse(endDate) : null;

        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLogEntity> logPage = auditLogRepository.findFiltered(
                severity, module, eventType, status, start, end, pageable
        );

        List<Map<String, Object>> items = logPage.getContent().stream()
                .map(this::entityToMap)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", items);
        response.put("total", logPage.getTotalElements());
        response.put("page", page);
        response.put("page_size", size);
        response.put("total_pages", logPage.getTotalPages());
        return response;
    }

    private Map<String, Object> entityToMap(AuditLogEntity entity) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", entity.getId().toString());
        item.put("action", entity.getEventType());
        item.put("event_type", entity.getEventTypeCompat());
        item.put("module", entity.getModule() != null ? entity.getModule() : "SYSTEM");
        item.put("severity", entity.getSeverity() != null ? entity.getSeverity() : "LOW");
        item.put("status", entity.getStatus());
        item.put("description", entity.getDetails());
        item.put("username", entity.getUsername());
        item.put("ip_address", entity.getIpAddress());
        item.put("endpoint", entity.getEndpoint());
        item.put("http_method", entity.getHttpMethod());
        item.put("response_code", entity.getResponseCode());
        item.put("duration_ms", entity.getDurationMs());
        item.put("metadata", entity.getMetadata());
        item.put("created_at", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : LocalDateTime.now().toString());
        item.put("timestamp", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : LocalDateTime.now().toString());
        item.put("user_id", entity.getUser() != null ? entity.getUser().getId().toString() : null);
        item.put("resource_type", entity.getResourceType());
        item.put("resource_id", entity.getResourceId());
        return item;
    }
}
