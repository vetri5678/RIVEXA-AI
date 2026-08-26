package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.dto.search.GlobalSearchDto;
import ai.riskvision.graveyard.dto.search.GlobalSearchDto.SearchResultItem;
import ai.riskvision.graveyard.entity.CodeFileAnalysisEntity;
import ai.riskvision.graveyard.entity.CodeFindingEntity;
import ai.riskvision.graveyard.entity.RepositoryEntity;
import ai.riskvision.graveyard.entity.UserEntity;
import ai.riskvision.graveyard.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class GlobalSearchService {

    private final UserRepository userRepository;
    private final RepositoryEntityRepository repoRepository;
    private final CodeFileAnalysisRepository fileAnalysisRepository;
    private final CodeFindingRepository findingRepository;

    private static final List<SearchResultItem> APP_PAGES = List.of(
            SearchResultItem.builder().id("page-dashboard").type("PAGE").title("Dashboard Overview").subtitle("Platform risk summary & live metrics").riskLevel("INFO").url("/dashboard").build(),
            SearchResultItem.builder().id("page-repos").type("PAGE").title("Repositories").subtitle("Manage & inspect synchronized codebases").riskLevel("INFO").url("/repositories").build(),
            SearchResultItem.builder().id("page-sync").type("PAGE").title("Repository Sync").subtitle("GitHub integration & OAuth metadata sync").riskLevel("INFO").url("/repo-sync").build(),
            SearchResultItem.builder().id("page-extraction").type("PAGE").title("Feature Extraction").subtitle("Extract XGBoost risk feature vectors").riskLevel("INFO").url("/feature-extraction").build(),
            SearchResultItem.builder().id("page-cleanse").type("PAGE").title("Data Cleanse Pipeline").subtitle("AST normalization & complexity metric aggregation").riskLevel("INFO").url("/data-cleanse").build(),
            SearchResultItem.builder().id("page-model").type("PAGE").title("XGBoost Model Engine").subtitle("Model versioning, retrain & TreeSHAP explanations").riskLevel("INFO").url("/model-engine").build(),
            SearchResultItem.builder().id("page-code-vision").type("PAGE").title("Code Vision AI").subtitle("Deep AST static analysis & line-level vulnerability scanner").riskLevel("INFO").url("/code-vision").build(),
            SearchResultItem.builder().id("page-run-prediction").type("PAGE").title("Run ML Prediction").subtitle("Execute repository failure probability prediction").riskLevel("INFO").url("/run-prediction").build(),
            SearchResultItem.builder().id("page-predictions").type("PAGE").title("Prediction Reports").subtitle("Historical predictions & risk analytics").riskLevel("INFO").url("/predictions").build(),
            SearchResultItem.builder().id("page-settings").type("PAGE").title("Settings & Credentials").subtitle("User profile, PAT tokens & system preferences").riskLevel("INFO").url("/settings").build()
    );

    @Transactional(readOnly = true)
    public GlobalSearchDto executeSearch(String callerEmail, String rawQuery, int limit) {
        if (callerEmail == null || callerEmail.isBlank()) {
            throw new IllegalArgumentException("Unauthenticated search request");
        }

        UserEntity user = userRepository.findByEmail(callerEmail.trim())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + callerEmail));

        String query = rawQuery != null ? rawQuery.trim() : "";
        int maxLimit = Math.min(Math.max(limit, 1), 30);
        List<SearchResultItem> results = new ArrayList<>();

        if (query.isEmpty()) {
            // Return top application navigation pages when query is empty
            return GlobalSearchDto.builder()
                    .query("")
                    .totalCount(APP_PAGES.size())
                    .results(APP_PAGES.subList(0, Math.min(maxLimit, APP_PAGES.size())))
                    .build();
        }

        String lowerQuery = query.toLowerCase();

        // 1. Search Repositories owned by caller
        Page<RepositoryEntity> repoPage = repoRepository.findAllByUserWithFilters(
                user.getId(), query, null, null, null, null, null, null, PageRequest.of(0, maxLimit)
        );
        for (RepositoryEntity r : repoPage.getContent()) {
            String sub = (r.getLanguage() != null ? r.getLanguage() : "Repository") + " · " +
                    (r.getOrganization() != null ? r.getOrganization() : (r.getOwner() != null ? r.getOwner() : "GitHub")) + " · " +
                    (r.getRiskLevel() != null ? r.getRiskLevel() : "LOW") + " Risk";
            results.add(SearchResultItem.builder()
                    .id("repo-" + r.getId())
                    .type("REPOSITORY")
                    .title(r.getRepositoryName() != null ? r.getRepositoryName() : "Repository")
                    .subtitle(sub)
                    .riskLevel(r.getRiskLevel() != null ? r.getRiskLevel().toUpperCase() : "LOW")
                    .url("/repositories")
                    .metadata(Map.of("repositoryId", r.getId().toString()))
                    .build());
        }

        // 2. Search Code Vision Source Files owned by caller
        Page<CodeFileAnalysisEntity> filePage = fileAnalysisRepository.searchUserFiles(user.getId(), query, PageRequest.of(0, maxLimit));
        for (CodeFileAnalysisEntity f : filePage.getContent()) {
            String sub = (f.getLanguage() != null ? f.getLanguage() : "File") + " · " + f.getLinesOfCode() + " LOC · " + f.getSeverity() + " Severity";
            results.add(SearchResultItem.builder()
                    .id("file-" + f.getId())
                    .type("SOURCE_FILE")
                    .title(f.getFilePath() != null ? f.getFilePath() : "Source File")
                    .subtitle(sub)
                    .riskLevel(f.getSeverity() != null ? f.getSeverity().toUpperCase() : "LOW")
                    .url("/code-vision?repoId=" + f.getRepositoryId() + "&fileId=" + f.getId())
                    .metadata(Map.of("repositoryId", f.getRepositoryId().toString(), "fileId", f.getId().toString()))
                    .build());
        }

        // 3. Search Code Findings owned by caller
        Page<CodeFindingEntity> findingPage = findingRepository.searchUserFindings(user.getId(), query, PageRequest.of(0, maxLimit));
        for (CodeFindingEntity f : findingPage.getContent()) {
            String sub = (f.getSymbolName() != null ? f.getSymbolName() : "Scope") + " · " + f.getFindingType() + " · " + f.getSeverity();
            results.add(SearchResultItem.builder()
                    .id("finding-" + f.getId())
                    .type("FINDING")
                    .title(f.getTitle() != null ? f.getTitle() : "Code Vulnerability Finding")
                    .subtitle(sub)
                    .riskLevel(f.getSeverity() != null ? f.getSeverity().toUpperCase() : "LOW")
                    .url("/code-vision?fileId=" + f.getFileAnalysisId())
                    .metadata(Map.of("findingId", f.getId().toString(), "fileAnalysisId", f.getFileAnalysisId().toString()))
                    .build());
        }

        // 4. Search Application Navigation Pages matching query
        for (SearchResultItem page : APP_PAGES) {
            if (page.getTitle().toLowerCase().contains(lowerQuery) || page.getSubtitle().toLowerCase().contains(lowerQuery)) {
                results.add(page);
            }
        }

        log.info("[GlobalSearch] User email={} query='{}' returned {} items", callerEmail, query, results.size());

        return GlobalSearchDto.builder()
                .query(query)
                .totalCount(results.size())
                .results(results.subList(0, Math.min(maxLimit, results.size())))
                .build();
    }
}
