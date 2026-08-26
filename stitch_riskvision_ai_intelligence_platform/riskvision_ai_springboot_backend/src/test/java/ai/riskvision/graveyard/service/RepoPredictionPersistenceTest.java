package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.entity.RepositoryEntity;
import ai.riskvision.graveyard.entity.RepositoryPredictionEntity;

import ai.riskvision.graveyard.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RepoPredictionPersistenceTest {

    @Mock
    private RepositoryEntityRepository repoRepository;

    @Mock
    private RepositoryPredictionEntityRepository predictionRepository;

    @Mock
    private RepositoryMetricsEntityRepository metricsRepository;

    @Mock
    private CodeFindingRepository findingRepository;

    @Mock
    private CodeFileAnalysisRepository fileAnalysisRepository;

    @Mock
    private CodeAnalysisRunRepository runRepository;

    @Mock
    private RepositorySyncService syncService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private OpenRouterService openRouterService;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private RepoPredictionService predictionService;

    private UUID repositoryId;
    private RepositoryEntity sampleEntity;

    @BeforeEach
    void setUp() {
        repositoryId = UUID.randomUUID();
        sampleEntity = RepositoryEntity.builder()
                .id(repositoryId)
                .repositoryName("test-repo")
                .repositoryUrl("https://github.com/owner/test-repo")
                .gitProvider("GITHUB")
                .status("ACTIVE")
                .openIssues(5)
                .contributors(3)
                .authTokenHint("secret-token-hint")
                .webhookSecret("secret-webhook-key")
                .build();
    }

    @Test
    @DisplayName("Normal prediction updates only prediction-related fields and protects secrets")
    @SuppressWarnings("unchecked")
    void testNormalPrediction_TargetedUpdateSuccess() throws Exception {
        when(repoRepository.findById(repositoryId)).thenReturn(Optional.of(sampleEntity));
        when(metricsRepository.findByRepositoryId(repositoryId)).thenReturn(Optional.empty());

        Map<String, Object> mlResponseBody = Map.of(
                "failure_probability", 0.35,
                "confidence_level", 88.0,
                "risk_category", "MEDIUM",
                "model_version", "xgboost-v2.4"
        );
        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(mlResponseBody, HttpStatus.OK);

        doReturn(responseEntity).when(restTemplate).exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        );

        when(openRouterService.getChatCompletion(anyString(), anyString())).thenReturn("{}");

        RepositoryPredictionEntity savedPrediction = RepositoryPredictionEntity.builder()
                .id(UUID.randomUUID())
                .repositoryId(repositoryId)
                .failureProbability(0.35)
                .riskScore(35)
                .riskLevel("MEDIUM")
                .healthScore(65.0)
                .predictionStatus("COMPLETED")
                .build();

        when(predictionRepository.save(any(RepositoryPredictionEntity.class))).thenReturn(savedPrediction);
        when(repoRepository.updatePredictionResults(eq(repositoryId), eq(0.35), eq(65.0), eq("MEDIUM"), eq(88.0), eq("COMPLETED")))
                .thenReturn(1);

        RepositoryPredictionEntity result = predictionService.runPrediction(repositoryId, "test-user");

        assertNotNull(result);
        assertEquals("COMPLETED", result.getPredictionStatus());
        assertEquals("MEDIUM", result.getRiskLevel());
        assertEquals(0.35, result.getFailureProbability());

        // Verify targeted JPQL update was called rather than full entity save
        verify(repoRepository, times(1)).updatePredictionResults(
                eq(repositoryId), eq(0.35), eq(65.0), eq("MEDIUM"), eq(88.0), eq("COMPLETED")
        );
        // Verify secrets were NOT updated via entity save
        verify(repoRepository, never()).save(any(RepositoryEntity.class));
    }

    @Test
    @DisplayName("Transient DB error during persistence triggers retry and succeeds")
    void testTransientDatabaseError_RetriesAndSucceeds() {
        AtomicInteger attempts = new AtomicInteger(0);

        when(predictionRepository.save(any(RepositoryPredictionEntity.class)))
                .thenAnswer(invocation -> {
                    if (attempts.incrementAndGet() == 1) {
                        throw new RuntimeException("PSQLException: An I/O error occurred while sending to the backend");
                    }
                    return RepositoryPredictionEntity.builder()
                            .id(UUID.randomUUID())
                            .repositoryId(repositoryId)
                            .predictionStatus("COMPLETED")
                            .build();
                });

        when(repoRepository.updatePredictionResults(any(), anyDouble(), anyDouble(), anyString(), anyDouble(), anyString()))
                .thenReturn(1);

        RepositoryPredictionEntity result = predictionService.persistPredictionWithRetry(
                repositoryId, 0.25, 25, "LOW", 90.0, 75.0, "xgboost-v2.4", null, null, "test-user"
        );

        assertNotNull(result);
        assertEquals(2, attempts.get()); // Succeeded on 2nd attempt after transient failure retry
    }

    @Test
    @DisplayName("ML service failure marks prediction status as FAILED in short transaction")
    @SuppressWarnings("unchecked")
    void testMlServiceFailure_MarksStatusFailed() {
        when(repoRepository.findById(repositoryId)).thenReturn(Optional.of(sampleEntity));

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenThrow(new RuntimeException("Connection refused to ML service"));

        assertThrows(IllegalStateException.class, () -> predictionService.runPrediction(repositoryId, "test-user"));

        // Verify status was updated to FAILED via targeted update
        verify(repoRepository).updatePredictionResults(eq(repositoryId), eq(0.0), eq(0.0), eq("UNKNOWN"), eq(0.0), eq("FAILED"));
    }
}
