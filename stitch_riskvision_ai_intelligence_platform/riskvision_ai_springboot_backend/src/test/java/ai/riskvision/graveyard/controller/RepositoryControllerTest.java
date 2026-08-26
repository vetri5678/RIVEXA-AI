package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.dto.repository.PagedRepositoryResponse;
import ai.riskvision.graveyard.dto.repository.RepositoryCreateRequest;
import ai.riskvision.graveyard.dto.repository.RepositoryResponse;
import ai.riskvision.graveyard.dto.repository.RepositorySummaryResponse;
import ai.riskvision.graveyard.repository.UserRepository;
import ai.riskvision.graveyard.service.RepositoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class RepositoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RepositoryService repositoryService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private ai.riskvision.graveyard.repository.RepositoryEntityRepository repositoryEntityRepository;

    @Test
    @WithMockUser(username = "admin@riskvision.ai", roles = {"USER"})
    public void testGetAllRepositories() throws Exception {
        RepositorySummaryResponse repo = RepositorySummaryResponse.builder()
                .id(UUID.randomUUID())
                .repositoryName("riskvision-core")
                .repositoryUrl("https://github.com/org/riskvision-core")
                .gitProvider("GITHUB")
                .build();

        PagedRepositoryResponse paged = PagedRepositoryResponse.builder()
                .content(Collections.singletonList(repo))
                .page(0)
                .size(20)
                .totalElements(1L)
                .totalPages(1)
                .last(true)
                .build();

        given(repositoryService.findAllByUser(
                any(), anyInt(), anyInt(), anyString(), anyString(),
                any(), any(), any(), any(), any(), any(), any()
        )).willReturn(paged);

        mockMvc.perform(get("/api/v1/repositories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].repository_name").value("riskvision-core"));
    }

    @Test
    @WithMockUser(username = "admin@riskvision.ai", roles = {"USER"})
    public void testCreateRepository() throws Exception {
        RepositoryCreateRequest req = new RepositoryCreateRequest();
        req.setRepositoryName("new-repo");
        req.setRepositoryUrl("https://github.com/org/new-repo");
        req.setGitProvider("GITHUB");

        RepositoryResponse repo = RepositoryResponse.builder()
                .id(UUID.randomUUID())
                .repositoryName("new-repo")
                .repositoryUrl("https://github.com/org/new-repo")
                .gitProvider("GITHUB")
                .build();

        given(repositoryService.createForUser(any(RepositoryCreateRequest.class), anyString())).willReturn(repo);

        mockMvc.perform(post("/api/v1/repositories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.repository_name").value("new-repo"));
    }

    @Test
    @WithMockUser(username = "admin@riskvision.ai", roles = {"USER"})
    public void testExportCsvSuccess() throws Exception {
        UUID repoId = UUID.randomUUID();
        RepositorySummaryResponse repo = RepositorySummaryResponse.builder()
                .id(repoId)
                .repositoryName("riskvision-analytics")
                .organization("RiskVision Corp")
                .gitProvider("GITHUB")
                .repositoryUrl("https://github.com/riskvision/analytics")
                .language("Java")
                .status("ACTIVE")
                .healthScore(92.5)
                .failureProbability(0.075)
                .riskLevel("LOW")
                .predictionStatus("COMPLETED")
                .aiConfidence(0.95)
                .build();

        PagedRepositoryResponse paged = PagedRepositoryResponse.builder()
                .content(Collections.singletonList(repo))
                .page(0)
                .size(10000)
                .totalElements(1L)
                .totalPages(1)
                .last(true)
                .build();

        given(repositoryService.findAllByUser(
                any(), anyInt(), anyInt(), anyString(), anyString(),
                any(), any(), any(), any(), any(), any(), any()
        )).willReturn(paged);

        mockMvc.perform(get("/api/v1/repositories/export/csv"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string(
                        org.springframework.http.HttpHeaders.CONTENT_TYPE, org.hamcrest.Matchers.containsString("text/csv")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string(
                        org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("attachment; filename=\"rivexa-repositories-")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                        org.hamcrest.Matchers.containsString("riskvision-analytics")));
    }

    @Test
    @WithMockUser(username = "admin@riskvision.ai", roles = {"USER"})
    public void testExportCsvFormulaInjectionAndEscaping() throws Exception {
        UUID repoId = UUID.randomUUID();
        RepositorySummaryResponse repo = RepositorySummaryResponse.builder()
                .id(repoId)
                .repositoryName("=SUM(A1:B10)")
                .organization("ACME \"Corp\"")
                .gitProvider("GITHUB")
                .build();

        PagedRepositoryResponse paged = PagedRepositoryResponse.builder()
                .content(Collections.singletonList(repo))
                .page(0)
                .size(10000)
                .totalElements(1L)
                .totalPages(1)
                .last(true)
                .build();

        given(repositoryService.findAllByUser(
                any(), anyInt(), anyInt(), anyString(), anyString(),
                any(), any(), any(), any(), any(), any(), any()
        )).willReturn(paged);

        mockMvc.perform(get("/api/v1/repositories/export/csv"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                        org.hamcrest.Matchers.containsString("'=SUM(A1:B10)")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                        org.hamcrest.Matchers.containsString("ACME \"\"Corp\"\"")));
    }

    @Test
    public void testExportCsvUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/repositories/export/csv"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin@riskvision.ai", roles = {"USER"})
    public void testExportCsvEmptyResults() throws Exception {
        PagedRepositoryResponse emptyPaged = PagedRepositoryResponse.builder()
                .content(Collections.emptyList())
                .page(0)
                .size(10000)
                .totalElements(0L)
                .totalPages(0)
                .last(true)
                .build();

        given(repositoryService.findAllByUser(
                any(), anyInt(), anyInt(), anyString(), anyString(),
                any(), any(), any(), any(), any(), any(), any()
        )).willReturn(emptyPaged);

        mockMvc.perform(get("/api/v1/repositories/export/csv"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                        org.hamcrest.Matchers.startsWith("ID,Repository Name,Owner / Organization")));
    }

    @Test
    @WithMockUser(username = "admin@riskvision.ai", roles = {"ADMIN"})
    public void testPredictionDebugEndpoint() throws Exception {
        UUID repoId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ai.riskvision.graveyard.entity.UserEntity mockUser = ai.riskvision.graveyard.entity.UserEntity.builder().id(userId).email("admin@riskvision.ai").build();
        ai.riskvision.graveyard.entity.RepositoryEntity mockRepoEntity = ai.riskvision.graveyard.entity.RepositoryEntity.builder().id(repoId).user(mockUser).build();

        given(userRepository.findByEmail("admin@riskvision.ai")).willReturn(java.util.Optional.of(mockUser));
        given(repositoryEntityRepository.findById(repoId)).willReturn(java.util.Optional.of(mockRepoEntity));

        ai.riskvision.graveyard.dto.repository.RepositoryDetailResponse detail = ai.riskvision.graveyard.dto.repository.RepositoryDetailResponse.builder()
                .id(repoId)
                .repositoryName("riskvision-debug-repo")
                .predictionStatus("COMPLETED")
                .healthScore(85.0)
                .riskLevel("LOW")
                .build();

        given(repositoryService.findById(repoId)).willReturn(detail);

        mockMvc.perform(get("/api/v1/repositories/" + repoId + "/prediction-debug"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositoryId").value(repoId.toString()))
                .andExpect(jsonPath("$.repositoryName").value("riskvision-debug-repo"))
                .andExpect(jsonPath("$.modelVersion").value("xgboost-v2.4"))
                .andExpect(jsonPath("$.featureSchemaValid").value(true));
    }
}
