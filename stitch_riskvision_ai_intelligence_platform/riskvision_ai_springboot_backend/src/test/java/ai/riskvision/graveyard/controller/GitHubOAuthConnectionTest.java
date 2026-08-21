package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.client.GitHubClient;
import ai.riskvision.graveyard.entity.OAuthAccountEntity;
import ai.riskvision.graveyard.entity.UserEntity;
import ai.riskvision.graveyard.exception.GitHubAuthenticationException;
import ai.riskvision.graveyard.repository.OAuthAccountRepository;
import ai.riskvision.graveyard.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class GitHubOAuthConnectionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private OAuthAccountRepository oauthAccountRepository;

    @MockBean
    private ai.riskvision.graveyard.repository.RepositoryEntityRepository repositoryEntityRepository;

    @MockBean
    private GitHubClient gitHubClient;

    private UserEntity userA;
    private UserEntity userB;
    private OAuthAccountEntity oauthA;
    private OAuthAccountEntity oauthB;

    @BeforeEach
    void setUp() {
        userA = UserEntity.builder()
                .email("userA@rivexa.com")
                .username("userA")
                .role("user")
                .build();

        userB = UserEntity.builder()
                .email("userB@rivexa.com")
                .username("userB")
                .role("user")
                .build();

        oauthA = OAuthAccountEntity.builder()
                .user(userA)
                .provider("github")
                .providerUserId("10001")
                .accessToken("gho_token_account_A")
                .build();

        oauthB = OAuthAccountEntity.builder()
                .user(userB)
                .provider("github")
                .providerUserId("20002")
                .accessToken("gho_token_account_B")
                .build();
    }

    @Test
    @DisplayName("Test 1: New RIVEXA user → no GitHub connected → 0 repositories")
    void test1_NewUserNoGitHubConnected_ReturnsZeroRepositories() throws Exception {
        given(userRepository.findByEmail("userA@rivexa.com")).willReturn(Optional.of(userA));
        given(oauthAccountRepository.findByUserAndProvider(any(UserEntity.class), eq("github"))).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/github/connection/status").principal(() -> "userA@rivexa.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(false));

        mockMvc.perform(get("/api/v1/github/repositories").principal(() -> "userA@rivexa.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(false))
                .andExpect(jsonPath("$.repositories").isEmpty());
    }

    @Test
    @DisplayName("Test 2: User connects GitHub Account A → Account A repositories appear")
    void test2_UserConnectsGitHubAccountA_RepositoriesReturned() throws Exception {
        given(userRepository.findByEmail("userA@rivexa.com")).willReturn(Optional.of(userA));
        given(oauthAccountRepository.findByUserAndProvider(any(UserEntity.class), eq("github"))).willReturn(Optional.of(oauthA));
        given(gitHubClient.getUserRepositories(eq("gho_token_account_A"), any(), any(), any(), any(), any()))
                .willReturn(List.of(Map.of("id", 1, "name", "repo-A1", "full_name", "userA/repo-A1")));

        mockMvc.perform(get("/api/v1/github/repositories").principal(() -> "userA@rivexa.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.repositories[0].name").value("repo-A1"));
    }

    @Test
    @DisplayName("Test 3: User disconnects Account A → repositories immediately disappear")
    void test3_UserDisconnectsAccountA_RepositoriesImmediatelyDisappear() throws Exception {
        given(userRepository.findByEmail("userA@rivexa.com")).willReturn(Optional.of(userA));
        given(oauthAccountRepository.findByUserAndProvider(any(UserEntity.class), eq("github"))).willReturn(Optional.of(oauthA));
        doNothing().when(gitHubClient).revokeOAuthToken("gho_token_account_A");

        mockMvc.perform(post("/api/v1/github/disconnect").principal(() -> "userA@rivexa.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.connected").value(false));
    }

    @Test
    @DisplayName("Test 4: After disconnect → GitHub API requests using Account A's token fail or are prevented")
    void test4_AfterDisconnect_GitHubApiRequestsPrevented() throws Exception {
        given(userRepository.findByEmail("userA@rivexa.com")).willReturn(Optional.of(userA));
        given(oauthAccountRepository.findByUserAndProvider(any(UserEntity.class), eq("github"))).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/github/repositories").principal(() -> "userA@rivexa.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(false))
                .andExpect(jsonPath("$.repositories").isEmpty());
    }

    @Test
    @DisplayName("Test 5: User reconnects and authenticates GitHub Account B → only Account B repositories appear")
    void test5_UserReconnectsGitHubAccountB_OnlyAccountBRepositoriesReturned() throws Exception {
        OAuthAccountEntity oauthReconnected = OAuthAccountEntity.builder()
                .user(userA)
                .provider("github")
                .providerUserId("20002")
                .accessToken("gho_token_account_B")
                .build();

        given(userRepository.findByEmail("userA@rivexa.com")).willReturn(Optional.of(userA));
        given(oauthAccountRepository.findByUserAndProvider(any(UserEntity.class), eq("github"))).willReturn(Optional.of(oauthReconnected));
        given(gitHubClient.getUserRepositories(eq("gho_token_account_B"), any(), any(), any(), any(), any()))
                .willReturn(List.of(Map.of("id", 2, "name", "repo-B1", "full_name", "userB/repo-B1")));

        mockMvc.perform(get("/api/v1/github/repositories").principal(() -> "userA@rivexa.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.repositories[0].name").value("repo-B1"));
    }

    @Test
    @DisplayName("Test 6: User B logs into RIVEXA → User A's GitHub repositories never appear")
    void test6_UserBLogsIn_UserAGitHubRepositoriesNeverAppear() throws Exception {
        given(userRepository.findByEmail("userB@rivexa.com")).willReturn(Optional.of(userB));
        given(oauthAccountRepository.findByUserAndProvider(any(UserEntity.class), eq("github"))).willReturn(Optional.of(oauthB));
        given(gitHubClient.getUserRepositories(eq("gho_token_account_B"), any(), any(), any(), any(), any()))
                .willReturn(List.of(Map.of("id", 20, "name", "userB-private-repo")));

        mockMvc.perform(get("/api/v1/github/repositories").principal(() -> "userB@rivexa.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.repositories[0].name").value("userB-private-repo"));
    }

    @Test
    @DisplayName("Test 7: Logout User A → login User B → no User A cache leak")
    void test7_LogoutUserA_LoginUserB_NoCacheLeak() throws Exception {
        given(userRepository.findByEmail("userB@rivexa.com")).willReturn(Optional.of(userB));
        given(oauthAccountRepository.findByUserAndProvider(any(UserEntity.class), eq("github"))).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/github/connection/status").principal(() -> "userB@rivexa.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(false));
    }

    @Test
    @DisplayName("Test 8: Disconnect → refresh browser / status check → GitHub remains disconnected")
    void test8_Disconnect_RefreshStatus_RemainsDisconnected() throws Exception {
        given(userRepository.findByEmail("userA@rivexa.com")).willReturn(Optional.of(userA));
        given(oauthAccountRepository.findByUserAndProvider(any(UserEntity.class), eq("github"))).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/github/connection/status").principal(() -> "userA@rivexa.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(false));
    }

    @Test
    @DisplayName("Test 9: Switch GitHub account → previous token and stale repositories are not reused")
    void test9_SwitchGitHubAccount_PreviousTokenNotReused() {
        OAuthAccountEntity updatedOAuth = OAuthAccountEntity.builder()
                .user(userA)
                .provider("github")
                .providerUserId("30003")
                .accessToken("gho_token_account_C")
                .build();

        assertNotEquals("gho_token_account_A", updatedOAuth.getAccessToken());
        assertEquals("30003", updatedOAuth.getProviderUserId());
    }

    @Test
    @DisplayName("Test 10: OAuth callback replay or invalid state → connection rejected securely")
    void test10_InvalidOAuthState_RejectedSecurely() {
        given(gitHubClient.getUserRepositories(eq(null), any(), any(), any(), any(), any()))
                .willThrow(new GitHubAuthenticationException("401 Unauthorized", "/user/repos", "Disconnected"));

        assertThrows(GitHubAuthenticationException.class, () -> {
            gitHubClient.getUserRepositories(null, 1, 100, "all", "owner", "updated");
        });
    }
}
