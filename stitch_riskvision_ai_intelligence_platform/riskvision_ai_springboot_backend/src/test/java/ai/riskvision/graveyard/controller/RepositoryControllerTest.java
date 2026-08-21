package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.dto.repository.PagedRepositoryResponse;
import ai.riskvision.graveyard.dto.repository.RepositoryCreateRequest;
import ai.riskvision.graveyard.dto.repository.RepositoryResponse;
import ai.riskvision.graveyard.dto.repository.RepositorySummaryResponse;
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
}
