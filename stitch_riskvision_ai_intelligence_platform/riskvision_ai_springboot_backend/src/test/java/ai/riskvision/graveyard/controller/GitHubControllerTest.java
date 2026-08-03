package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.client.GitHubClient;
import ai.riskvision.graveyard.config.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class GitHubControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GitHubClient gitHubClient;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void testHealthEndpointReturns200() throws Exception {
        given(gitHubClient.getHealthStatus()).willReturn(Map.of(
                "status", "UP",
                "pat_configured", true,
                "pat_valid", true,
                "authenticated_user", "octocat"
        ));

        mockMvc.perform(get("/api/github/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.authenticated_user").value("octocat"));
    }

    @Test
    @WithMockUser
    void testGetRepositoryMetadataReturns200() throws Exception {
        given(gitHubClient.getRepositoryMetadata("octocat", "hello-world")).willReturn(Map.of(
                "name", "hello-world",
                "owner", Map.of("login", "octocat"),
                "stargazers_count", 100
        ));

        mockMvc.perform(get("/api/v1/github/repos/octocat/hello-world"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("hello-world"))
                .andExpect(jsonPath("$.stargazers_count").value(100));
    }
}
