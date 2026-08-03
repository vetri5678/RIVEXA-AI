package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.service.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AIControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TelemetrySummaryService telemetrySummaryService;

    @MockBean
    private RiskExplanationService riskExplanationService;

    @MockBean
    private AuditAnalysisService auditAnalysisService;

    @MockBean
    private RepositoryAnalysisService repositoryAnalysisService;

    @Test
    @WithMockUser(username = "admin", roles = {"admin"})
    void testGetTelemetryAnalysisAuthorized() throws Exception {
        String mockResponse = "{\"summary\":\"Nominal performance\"}";
        Mockito.when(telemetrySummaryService.analyzeTelemetry()).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/ai/telemetry-analysis"))
                .andExpect(status().isOk())
                .andExpect(content().string(mockResponse));
    }

    @Test
    void testGetTelemetryAnalysisUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/ai/telemetry-analysis"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"admin"})
    void testRepositoryRiskAnalysis() throws Exception {
        UUID repoId = UUID.randomUUID();
        String mockResponse = "{\"summary\":\"High risk repository analysis\"}";
        Mockito.when(repositoryAnalysisService.analyzeRepository(repoId)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/ai/repository/" + repoId + "/risk-analysis"))
                .andExpect(status().isOk())
                .andExpect(content().string(mockResponse));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"admin"})
    void testExplainEvent() throws Exception {
        String eventPayload = "{\"action\":\"FAILED_LOGIN\",\"description\":\"Failed login attempt for admin\"}";
        String mockResponse = "{\"summary\":\"Brute force risk suspected\"}";
        Mockito.when(auditAnalysisService.explainEvent(Mockito.any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/ai/explain-event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventPayload))
                .andExpect(status().isOk())
                .andExpect(content().string(mockResponse));
    }
}
