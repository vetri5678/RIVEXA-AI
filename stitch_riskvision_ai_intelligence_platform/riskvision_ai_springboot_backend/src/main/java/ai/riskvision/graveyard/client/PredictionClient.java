package ai.riskvision.graveyard.client;

import ai.riskvision.graveyard.dto.prediction.PredictionRequestDTO;
import ai.riskvision.graveyard.dto.prediction.PredictionResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * HTTP REST client for the FastAPI ML Prediction Service.
 * Communicates with the Python FastAPI ML backend over HTTP.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PredictionClient {

    private final RestTemplate restTemplate;

    @Value("${ml.service.url:http://localhost:5000}")
    private String mlServiceBaseUrl;

    private static final String PREDICT_PATH    = "/api/v1/ml/predict";
    private static final String METRICS_PATH    = "/api/v1/ml/metrics";
    private static final String MODEL_PATH      = "/api/v1/ml/model";
    private static final String HEALTH_PATH     = "/api/v1/ml/health";
    private static final String VERSION_PATH    = "/api/v1/ml/version";
    private static final String FEATURES_PATH   = "/api/v1/ml/feature-importance";
    private static final String HISTORY_PATH    = "/api/v1/ml/prediction-history";

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE_REF =
            new ParameterizedTypeReference<Map<String, Object>>() {};

    /**
     * Send a single project prediction to FastAPI ML service.
     */
    public PredictionResponseDTO predict(PredictionRequestDTO request) {
        String url = mlServiceBaseUrl + PREDICT_PATH;
        log.info("[PredictionClient] POST {} — requesting ML prediction", url);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<PredictionRequestDTO> entity = new HttpEntity<>(request, headers);

            ResponseEntity<PredictionResponseDTO> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, PredictionResponseDTO.class
            );
            PredictionResponseDTO result = response.getBody();
            log.info("[PredictionClient] Prediction response: riskLevel={} riskScore={}",
                    result != null ? result.getRiskLevel() : "null",
                    result != null ? result.getRiskScore() : "null");
            return result;

        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("[PredictionClient] FastAPI ML service returned error status={} body={} error={}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e.getMessage(), e);
            throw new RuntimeException("ML service error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            log.error("[PredictionClient] FastAPI ML service unreachable at {}: {}", url, e.getMessage());
            throw new RuntimeException("ML service unavailable: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("[PredictionClient] Unexpected error calling ML service: {}", e.getMessage(), e);
            throw new RuntimeException("Prediction request failed: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieve model evaluation metrics from FastAPI ML service.
     */
    public Map<String, Object> getMetrics() {
        String url = mlServiceBaseUrl + METRICS_PATH;
        log.debug("[PredictionClient] GET {}", url);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.GET, null, MAP_TYPE_REF);
            return response.getBody();
        } catch (Exception e) {
            log.warn("[PredictionClient] Failed to fetch metrics from ML service: {}", e.getMessage());
            return Map.of("error", "ML service unavailable");
        }
    }

    /**
     * Retrieve full model metadata from FastAPI ML service.
     */
    public Map<String, Object> getModelInfo() {
        String url = mlServiceBaseUrl + MODEL_PATH;
        log.debug("[PredictionClient] GET {}", url);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.GET, null, MAP_TYPE_REF);
            return response.getBody();
        } catch (Exception e) {
            log.warn("[PredictionClient] Failed to fetch model info: {}", e.getMessage());
            return Map.of("error", "ML service unavailable");
        }
    }

    /**
     * Retrieve feature importance ranking from FastAPI ML service.
     */
    public Map<String, Object> getFeatureImportance() {
        String url = mlServiceBaseUrl + FEATURES_PATH;
        log.debug("[PredictionClient] GET {}", url);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.GET, null, MAP_TYPE_REF);
            return response.getBody();
        } catch (Exception e) {
            log.warn("[PredictionClient] Failed to fetch feature importance: {}", e.getMessage());
            return Map.of("error", "ML service unavailable");
        }
    }

    /**
     * Retrieve ML service health from FastAPI.
     */
    public Map<String, Object> getHealth() {
        String url = mlServiceBaseUrl + HEALTH_PATH;
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.GET, null, MAP_TYPE_REF);
            return response.getBody();
        } catch (Exception e) {
            log.warn("[PredictionClient] ML service health check failed: {}", e.getMessage());
            return Map.of("status", "unreachable", "model_loaded", false);
        }
    }

    /**
     * Retrieve ML service version from FastAPI.
     */
    public Map<String, Object> getVersion() {
        String url = mlServiceBaseUrl + VERSION_PATH;
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.GET, null, MAP_TYPE_REF);
            return response.getBody();
        } catch (Exception e) {
            log.warn("[PredictionClient] ML service version check failed: {}", e.getMessage());
            return Map.of("modelVersion", "unknown");
        }
    }

    /**
     * Retrieve in-memory prediction history from FastAPI.
     */
    public Map<String, Object> getPredictionHistory(int limit) {
        String url = mlServiceBaseUrl + HISTORY_PATH + "?limit=" + limit;
        log.debug("[PredictionClient] GET {}", url);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.GET, null, MAP_TYPE_REF);
            return response.getBody();
        } catch (Exception e) {
            log.warn("[PredictionClient] Failed to fetch prediction history: {}", e.getMessage());
            return Map.of("total", 0, "items", List.of());
        }
    }
}

