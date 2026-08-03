package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/audit")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class AuditController {

    private final AuditLogService auditLogService;

    @GetMapping("/logs")
    public ResponseEntity<Map<String, Object>> getAuditLogs(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        log.debug("HTTP GET /api/v1/audit/logs requested page={}, size={}", page, size);
        return ResponseEntity.ok(auditLogService.getAuditLogs(page, size));
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        log.debug("HTTP GET /api/v1/audit/statistics requested");
        return ResponseEntity.ok(auditLogService.getStatistics());
    }

    @GetMapping("/live")
    public ResponseEntity<Map<String, Object>> getLiveEvents(
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        log.debug("HTTP GET /api/v1/audit/live requested limit={}", limit);
        return ResponseEntity.ok(auditLogService.getRecentLive(limit));
    }

    @PostMapping("/filter")
    public ResponseEntity<Map<String, Object>> filterLogs(@RequestBody Map<String, Object> filters) {
        log.debug("HTTP POST /api/v1/audit/filter requested");
        String severity = (String) filters.get("severity");
        String module = (String) filters.get("module");
        String eventType = (String) filters.get("event_type");
        String status = (String) filters.get("status");
        String startDate = (String) filters.get("start_date");
        String endDate = (String) filters.get("end_date");
        int page = filters.containsKey("page") ? ((Number) filters.get("page")).intValue() : 0;
        int size = filters.containsKey("size") ? ((Number) filters.get("size")).intValue() : 20;
        return ResponseEntity.ok(auditLogService.filterLogs(severity, module, eventType, status, startDate, endDate, page, size));
    }
}
