package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.dto.auth.TokenResponse;
import ai.riskvision.graveyard.dto.auth.UserLoginRequest;
import ai.riskvision.graveyard.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    public void testLoginSuccess() throws Exception {
        UserLoginRequest loginRequest = new UserLoginRequest();
        loginRequest.setEmail("admin@riskvision.ai");
        loginRequest.setPassword("Admin123!");

        TokenResponse tokenResponse = TokenResponse.builder()
                .accessToken("mock-jwt-token-12345")
                .refreshToken("mock-refresh-token-67890")
                .tokenType("Bearer")
                .build();

        given(authService.login(any(UserLoginRequest.class))).willReturn(tokenResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("mock-jwt-token-12345"))
                .andExpect(jsonPath("$.token_type").value("Bearer"));
    }
}
