package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.dto.prediction.PredictionRequestDTO;
import ai.riskvision.graveyard.dto.prediction.PredictionResponseDTO;
import ai.riskvision.graveyard.entity.PredictionHistoryEntity;
import ai.riskvision.graveyard.service.PredictionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PredictionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PredictionService predictionService;

    @Test
    void testPredictUnauthorized() throws Exception {
        PredictionRequestDTO request = PredictionRequestDTO.builder()
                .projectBudget(100000.0)
                .actualCost(80000.0)
                .scheduleDelay(5.0)
                .teamSize(8)
                .openIssues(12)
                .criticalBugs(2)
                .completionPct(75.0)
                .clientRequirementChanges(4)
                .priority("HIGH")
                .department("ENGINEERING")
                .projectType("SOFTWARE")
                .build();

        mockMvc.perform(post("/api/v1/ml/predict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"admin"})
    void testPredictAuthorized() throws Exception {
        PredictionRequestDTO request = PredictionRequestDTO.builder()
                .projectBudget(100000.0)
                .actualCost(80000.0)
                .scheduleDelay(5.0)
                .teamSize(8)
                .openIssues(12)
                .criticalBugs(2)
                .completionPct(75.0)
                .clientRequirementChanges(4)
                .priority("HIGH")
                .department("ENGINEERING")
                .projectType("SOFTWARE")
                .build();

        PredictionResponseDTO mockResponse = PredictionResponseDTO.builder()
                .id("test-uuid")
                .riskScore(45.5)
                .riskLevel("MEDIUM")
                .confidence(0.85)
                .probability(0.62)
                .topFactors(List.of("Critical Bugs", "Schedule Delay"))
                .modelVersion("1.0.0")
                .build();

        Mockito.when(predictionService.runPrediction(Mockito.any(), Mockito.any(), Mockito.eq("admin")))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/ml/predict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("test-uuid"))
                .andExpect(jsonPath("$.risk_level").value("MEDIUM"))
                .andExpect(jsonPath("$.risk_score").value(45.5));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"admin"})
    void testGetMetrics() throws Exception {
        Map<String, Object> mockMetrics = Map.of("accuracy", 0.92, "f1_score", 0.90);
        Mockito.when(predictionService.getMetrics()).thenReturn(mockMetrics);

        mockMvc.perform(get("/api/v1/ml/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accuracy").value(0.92))
                .andExpect(jsonPath("$.f1_score").value(0.90));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"admin"})
    void testGetAnalytics() throws Exception {
        Map<String, Object> mockAnalytics = Map.of("total_predictions", 120L, "high_risk_count", 15L);
        Mockito.when(predictionService.getAnalyticsSummary()).thenReturn(mockAnalytics);

        mockMvc.perform(get("/api/v1/ml/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_predictions").value(120))
                .andExpect(jsonPath("$.high_risk_count").value(15));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"admin"})
    void testGetHistory() throws Exception {
        PredictionHistoryEntity entity = PredictionHistoryEntity.builder()
                .id("test-history-id")
                .projectId("project-123")
                .riskScore(72.0)
                .riskLevel("HIGH")
                .confidence(0.94)
                .modelVersion("1.0.0")
                .createdBy("admin")
                .build();

        Mockito.when(predictionService.getRecentHistory()).thenReturn(List.of(entity));

        mockMvc.perform(get("/api/v1/ml/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("test-history-id"))
                .andExpect(jsonPath("$[0].risk_level").value("HIGH"));
    }
}
