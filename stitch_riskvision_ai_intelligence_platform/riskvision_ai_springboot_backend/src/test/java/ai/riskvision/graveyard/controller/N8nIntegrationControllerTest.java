package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.service.N8nWebhookService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
public class N8nIntegrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private N8nWebhookService n8nWebhookService;

    @Test
    @DisplayName("GET /api/v1/system/integrations/n8n/status returns integration telemetry")
    void testGetIntegrationStatus_ReturnsOk() throws Exception {
        Map<String, Object> mockStatus = new LinkedHashMap<>();
        mockStatus.put("enabled", true);
        mockStatus.put("baseUrl", "http://localhost:5678/webhook");
        mockStatus.put("connectTimeoutMs", 2000);
        mockStatus.put("readTimeoutMs", 3000);
        mockStatus.put("maxRetries", 2);
        mockStatus.put("riskAlertThreshold", 80.0);
        mockStatus.put("successCount", 5L);
        mockStatus.put("failureCount", 0L);

        given(n8nWebhookService.getIntegrationStatus()).willReturn(mockStatus);

        mockMvc.perform(get("/api/v1/system/integrations/n8n/status")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.baseUrl").value("http://localhost:5678/webhook"))
                .andExpect(jsonPath("$.successCount").value(5))
                .andExpect(jsonPath("$.riskAlertThreshold").value(80.0));
    }
}
