package ai.riskvision.graveyard.client;

import ai.riskvision.graveyard.config.OpenRouterConfiguration;
import ai.riskvision.graveyard.dto.ai.OpenRouterResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@RestClientTest({OpenRouterClient.class, OpenRouterConfiguration.class})
@TestPropertySource(properties = {
        "openrouter.api-key=test-api-key",
        "openrouter.base-url=http://localhost",
        "openrouter.default-model=openrouter/free",
        "openrouter.fallback-models=deepseek/deepseek-chat,qwen/qwen3-coder",
        "openrouter.timeout-seconds=5",
        "openrouter.max-retries=1"
})
class OpenRouterClientTest {

    @Autowired
    private OpenRouterClient client;

    @Autowired
    private MockRestServiceServer server;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testSuccessfulCompletion() throws Exception {
        OpenRouterResponseDTO.ChoiceDTO.MessageDTO message = OpenRouterResponseDTO.ChoiceDTO.MessageDTO.builder()
                .role("assistant")
                .content("{\"summary\":\"Perfect match\"}")
                .build();
        OpenRouterResponseDTO.ChoiceDTO choice = OpenRouterResponseDTO.ChoiceDTO.builder()
                .message(message)
                .build();
        OpenRouterResponseDTO mockResponse = OpenRouterResponseDTO.builder()
                .choices(List.of(choice))
                .build();

        String jsonResponse = objectMapper.writeValueAsString(mockResponse);

        server.expect(requestTo("http://localhost/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        String result = client.getCompletion("System instruction", "User context");

        assertNotNull(result);
        assertTrue(result.contains("Perfect match"));
        server.verify();
    }

    @Test
    void testRetryOnRateLimitAndThenSuccess() throws Exception {
        OpenRouterResponseDTO.ChoiceDTO.MessageDTO message = OpenRouterResponseDTO.ChoiceDTO.MessageDTO.builder()
                .role("assistant")
                .content("{\"summary\":\"Retry worked\"}")
                .build();
        OpenRouterResponseDTO.ChoiceDTO choice = OpenRouterResponseDTO.ChoiceDTO.builder()
                .message(message)
                .build();
        OpenRouterResponseDTO mockResponse = OpenRouterResponseDTO.builder()
                .choices(List.of(choice))
                .build();

        String jsonResponse = objectMapper.writeValueAsString(mockResponse);

        // 1st request gets 429
        server.expect(requestTo("http://localhost/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        // 2nd request succeeds
        server.expect(requestTo("http://localhost/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        String result = client.getCompletion("System instruction", "User context");

        assertNotNull(result);
        assertTrue(result.contains("Retry worked"));
        server.verify();
    }

    @Test
    void testFallbackModelTriggeredOnFailure() throws Exception {
        OpenRouterResponseDTO.ChoiceDTO.MessageDTO message = OpenRouterResponseDTO.ChoiceDTO.MessageDTO.builder()
                .role("assistant")
                .content("{\"summary\":\"Fallback worked\"}")
                .build();
        OpenRouterResponseDTO.ChoiceDTO choice = OpenRouterResponseDTO.ChoiceDTO.builder()
                .message(message)
                .build();
        OpenRouterResponseDTO mockResponse = OpenRouterResponseDTO.builder()
                .choices(List.of(choice))
                .build();

        String jsonResponse = objectMapper.writeValueAsString(mockResponse);

        // Default model fails completely (both try and retry get 500)
        server.expect(requestTo("http://localhost/chat/completions"))
                .andExpect(jsonPath("$.model").value("openrouter/free"))
                .andRespond(withServerError());

        server.expect(requestTo("http://localhost/chat/completions"))
                .andExpect(jsonPath("$.model").value("openrouter/free"))
                .andRespond(withServerError());

        // Fallback model deepseek/deepseek-chat succeeds on first try
        server.expect(requestTo("http://localhost/chat/completions"))
                .andExpect(jsonPath("$.model").value("deepseek/deepseek-chat"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        String result = client.getCompletion("System instruction", "User context");

        assertNotNull(result);
        assertTrue(result.contains("Fallback worked"));
        server.verify();
    }
}
