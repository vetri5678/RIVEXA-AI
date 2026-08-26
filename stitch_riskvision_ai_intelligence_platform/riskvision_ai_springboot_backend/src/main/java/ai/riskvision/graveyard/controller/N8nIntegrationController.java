package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.service.N8nWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * N8nIntegrationController — Exposes n8n automation engine status and telemetry health endpoints.
 */
@RestController
@RequestMapping("/api/v1/system/integrations/n8n")
@RequiredArgsConstructor
@Slf4j
public class N8nIntegrationController {

    private final N8nWebhookService n8nWebhookService;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getIntegrationStatus() {
        log.info("HTTP GET /api/v1/system/integrations/n8n/status requested");
        Map<String, Object> status = n8nWebhookService.getIntegrationStatus();
        return ResponseEntity.ok(status);
    }
}
