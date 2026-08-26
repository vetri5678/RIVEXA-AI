package ai.riskvision.graveyard.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class N8nWebhookServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private N8nWebhookService webhookService;

    @BeforeEach
    void setUp() {
        webhookService = new N8nWebhookService(restTemplate);
        webhookService.setWebhookEnabled(true);
        webhookService.setConnectTimeoutMs(500);
        webhookService.setReadTimeoutMs(500);
        webhookService.setMaxRetries(1);
        webhookService.setRegistrationWebhookUrl("http://localhost:5678/webhook/registration-verification");
        webhookService.setPredictionCompletedWebhookUrl("http://localhost:5678/webhook/prediction-completed");
        webhookService.setRepositorySyncWebhookUrl("http://localhost:5678/webhook/repository-sync");
        webhookService.setWebhookSecret("test_secret");
    }

    @Test
    @DisplayName("Test 1: Valid Webhook URL returns success status")
    void testSendWebhook_ValidUrl_Success() {
        Map<String, Object> payload = Map.of("event", "TEST_EVENT");
        String url = "http://localhost:5678/webhook/test";

        when(restTemplate.postForEntity(eq(url), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("OK", HttpStatus.OK));

        boolean result = webhookService.sendWebhook(url, payload, "TEST_EVENT");

        assertTrue(result, "Valid webhook delivery should return true");
        verify(restTemplate, times(1)).postForEntity(eq(url), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("Test 2: Missing / empty Webhook URL skips dispatch gracefully")
    void testSendWebhook_MissingUrl_GracefulSkip() {
        Map<String, Object> payload = Map.of("event", "TEST_EVENT");

        boolean resultNull = webhookService.sendWebhook(null, payload, "TEST_EVENT");
        boolean resultEmpty = webhookService.sendWebhook("   ", payload, "TEST_EVENT");

        assertFalse(resultNull, "Null webhook URL should return false gracefully");
        assertFalse(resultEmpty, "Empty webhook URL should return false gracefully");
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("Test 3: Invalid Webhook URL format skips dispatch gracefully")
    void testSendWebhook_InvalidUrl_GracefulSkip() {
        Map<String, Object> payload = Map.of("event", "TEST_EVENT");

        boolean result = webhookService.sendWebhook("ftp://invalid-protocol.com", payload, "TEST_EVENT");

        assertFalse(result, "Invalid scheme should return false gracefully");
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("Test 4: Timeout exception fails gracefully without crashing")
    void testSendWebhook_Timeout_GracefulFallback() {
        Map<String, Object> payload = Map.of("event", "TEST_EVENT");
        String url = "http://localhost:5678/webhook/timeout";

        when(restTemplate.postForEntity(eq(url), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Read timed out"));

        boolean result = webhookService.sendWebhook(url, payload, "TEST_EVENT");

        assertFalse(result, "Timeout should return false");
        verify(restTemplate, times(2)).postForEntity(eq(url), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("Test 5: HTTP 500 Server Error retries and returns false safely")
    void testSendWebhook_Http500_RetriesAndGracefulFallback() {
        Map<String, Object> payload = Map.of("event", "TEST_EVENT");
        String url = "http://localhost:5678/webhook/error500";

        when(restTemplate.postForEntity(eq(url), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error"));

        boolean result = webhookService.sendWebhook(url, payload, "TEST_EVENT");

        assertFalse(result, "HTTP 500 should fail gracefully and return false");
        verify(restTemplate, times(2)).postForEntity(eq(url), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("Test 6: Unavailable / Connection Refused endpoint fails gracefully")
    void testSendWebhook_UnavailableEndpoint_GracefulFallback() {
        Map<String, Object> payload = Map.of("event", "TEST_EVENT");
        String url = "http://localhost:5678/webhook/offline";

        when(restTemplate.postForEntity(eq(url), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("Connection refused"));

        boolean result = webhookService.sendWebhook(url, payload, "TEST_EVENT");

        assertFalse(result, "Connection refused should return false gracefully");
        verify(restTemplate, times(2)).postForEntity(eq(url), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("Test 7: Disabled Webhook flag skips execution completely")
    void testSendWebhook_DisabledFlag_GracefulSkip() {
        webhookService.setWebhookEnabled(false);
        Map<String, Object> payload = Map.of("event", "TEST_EVENT");

        boolean result = webhookService.sendWebhook("http://localhost:5678/webhook/test", payload, "TEST_EVENT");

        assertFalse(result, "Disabled webhook service should return false immediately");
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("Test 8: Prediction Completed Webhook executes safely without throwing exceptions")
    void testTriggerPredictionCompletedWebhook_NonBlocking() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("OK", HttpStatus.OK));

        assertDoesNotThrow(() ->
                webhookService.triggerPredictionCompletedWebhook(
                        "pred-123",
                        "repo-456",
                        "HIGH",
                        0.82,
                        0.95,
                        "ANALYST"
                )
        );
    }

    @Test
    @DisplayName("Test 9: Repository Sync Webhook executes safely without throwing exceptions")
    void testTriggerRepositorySyncWebhook_NonBlocking() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("OK", HttpStatus.OK));

        assertDoesNotThrow(() ->
                webhookService.triggerRepositorySyncWebhook(
                        "repo-456",
                        "rivexa-ai",
                        "GITHUB",
                        true,
                        "Sync complete"
                )
        );
    }
}
