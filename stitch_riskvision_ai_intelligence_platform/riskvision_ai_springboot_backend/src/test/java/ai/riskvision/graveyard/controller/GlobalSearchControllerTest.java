package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.dto.search.GlobalSearchDto;
import ai.riskvision.graveyard.service.GlobalSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class GlobalSearchControllerTest {

    @Mock
    private GlobalSearchService globalSearchService;

    @InjectMocks
    private GlobalSearchController globalSearchController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("globalSearch: returns user-scoped search results for valid query")
    void testGlobalSearchSuccess() {
        Principal principal = () -> "testuser@riskvision.ai";

        GlobalSearchDto mockResponse = GlobalSearchDto.builder()
                .query("repo")
                .totalCount(2)
                .results(List.of(
                        GlobalSearchDto.SearchResultItem.builder()
                                .id("repo-1")
                                .type("REPOSITORY")
                                .title("RIVEXA Backend")
                                .subtitle("Java · Spring Boot · HIGH Risk")
                                .riskLevel("HIGH")
                                .url("/repositories")
                                .build(),
                        GlobalSearchDto.SearchResultItem.builder()
                                .id("page-1")
                                .type("PAGE")
                                .title("Repositories")
                                .subtitle("Manage codebases")
                                .riskLevel("INFO")
                                .url("/repositories")
                                .build()
                ))
                .build();

        when(globalSearchService.executeSearch(eq("testuser@riskvision.ai"), eq("repo"), eq(10)))
                .thenReturn(mockResponse);

        ResponseEntity<GlobalSearchDto> response = globalSearchController.globalSearch("repo", 10, principal);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getQuery()).isEqualTo("repo");
        assertThat(response.getBody().getResults()).hasSize(2);
        assertThat(response.getBody().getResults().get(0).getType()).isEqualTo("REPOSITORY");
    }

    @Test
    @DisplayName("globalSearch: handles empty query returning page navigation items")
    void testGlobalSearchEmptyQuery() {
        Principal principal = () -> "user@example.com";

        GlobalSearchDto emptyQueryResponse = GlobalSearchDto.builder()
                .query("")
                .totalCount(10)
                .results(List.of(
                        GlobalSearchDto.SearchResultItem.builder()
                                .id("page-dashboard")
                                .type("PAGE")
                                .title("Dashboard Overview")
                                .url("/dashboard")
                                .build()
                ))
                .build();

        when(globalSearchService.executeSearch(eq("user@example.com"), eq(""), eq(10)))
                .thenReturn(emptyQueryResponse);

        ResponseEntity<GlobalSearchDto> response = globalSearchController.globalSearch("", 10, principal);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResults()).isNotEmpty();
        assertThat(response.getBody().getResults().get(0).getType()).isEqualTo("PAGE");
    }
}
