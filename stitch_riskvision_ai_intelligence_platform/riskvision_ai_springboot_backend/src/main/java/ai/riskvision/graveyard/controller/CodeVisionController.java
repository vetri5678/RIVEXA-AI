package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.dto.codevision.*;
import ai.riskvision.graveyard.entity.RepositoryEntity;
import ai.riskvision.graveyard.entity.UserEntity;
import ai.riskvision.graveyard.repository.RepositoryEntityRepository;
import ai.riskvision.graveyard.repository.UserRepository;
import ai.riskvision.graveyard.service.CodeVisionJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/repositories")
@RequiredArgsConstructor
@Slf4j
public class CodeVisionController {

    private final CodeVisionJobService codeVisionJobService;
    private final RepositoryEntityRepository repositoryRepository;
    private final UserRepository userRepository;

    private UserEntity resolveAuthenticatedUser(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new IllegalStateException("Authentication required");
        }
        return userRepository.findByEmail(principal.getName())
                .or(() -> userRepository.findByUsername(principal.getName()))
                .orElseThrow(() -> new IllegalStateException("User not found: " + principal.getName()));
    }

    private void verifyRepositoryOwnership(RepositoryEntity repo, UserEntity user) {
        if (repo.getUser() != null && user != null && !repo.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Access denied: You do not own this repository.");
        }
    }

    /**
     * POST /api/v1/repositories/{repositoryId}/code-analysis
     * Queues/starts Code Vision AI analysis for a repository.
     */
    @PostMapping("/{repositoryId}/code-analysis")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CodeAnalysisRunResponse> startCodeAnalysis(
            @PathVariable UUID repositoryId,
            @RequestParam(value = "force", defaultValue = "false") boolean force,
            Principal principal) {

        UserEntity user = resolveAuthenticatedUser(principal);
        RepositoryEntity repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        verifyRepositoryOwnership(repo, user);

        log.info("[CodeVisionController] POST /code-analysis — repoId={} user={} force={}", repositoryId, user.getEmail(), force);
        CodeAnalysisRunResponse run = codeVisionJobService.startOrQueueAnalysis(repositoryId, user.getId(), force);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(run);
    }

    /**
     * POST /api/v1/repositories/code-analysis/batch
     * Queues/starts Code Vision AI analysis for multiple repositories in batch.
     */
    @PostMapping("/code-analysis/batch")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BatchCodeAnalysisResponse> startBatchCodeAnalysis(
            @RequestBody BatchCodeAnalysisRequest request,
            Principal principal) {

        UserEntity user = resolveAuthenticatedUser(principal);
        log.info("[CodeVisionController] POST /code-analysis/batch — user={} reposCount={} force={}",
                user.getEmail(), request != null && request.getRepositoryIds() != null ? request.getRepositoryIds().size() : 0,
                request != null && request.isForce());

        if (request == null || request.getRepositoryIds() == null || request.getRepositoryIds().isEmpty()) {
            throw new IllegalArgumentException("Batch analysis request requires at least one repository ID.");
        }

        BatchCodeAnalysisResponse response = codeVisionJobService.startBatchAnalysis(
                request.getRepositoryIds(), user.getId(), request.isForce()
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * GET /api/v1/repositories/{repositoryId}/code-analysis/latest
     * Retrieves summary & latest completed run info.
     */
    @GetMapping("/{repositoryId}/code-analysis/latest")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CodeVisionSummaryResponse> getLatestSummary(
            @PathVariable UUID repositoryId,
            Principal principal) {

        UserEntity user = resolveAuthenticatedUser(principal);
        RepositoryEntity repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        verifyRepositoryOwnership(repo, user);

        CodeVisionSummaryResponse summary = codeVisionJobService.getLatestSummary(repositoryId, user.getId());
        return ResponseEntity.ok(summary);
    }

    /**
     * GET /api/v1/repositories/{repositoryId}/code-analysis/status
     * Retrieves current job status / progress.
     */
    @GetMapping("/{repositoryId}/code-analysis/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CodeVisionSummaryResponse> getStatus(
            @PathVariable UUID repositoryId,
            Principal principal) {

        return getLatestSummary(repositoryId, principal);
    }

    /**
     * GET /api/v1/repositories/{repositoryId}/code-analysis/files
     * Retrieves paginated list of analyzed files with risk scores and severity.
     */
    @GetMapping("/{repositoryId}/code-analysis/files")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<CodeFileAnalysisResponse>> getFileAnalyses(
            @PathVariable UUID repositoryId,
            @RequestParam(value = "severity", required = false) String severity,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sortBy", defaultValue = "riskScore") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir,
            Principal principal) {

        UserEntity user = resolveAuthenticatedUser(principal);
        RepositoryEntity repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        verifyRepositoryOwnership(repo, user);

        Page<CodeFileAnalysisResponse> pageResult = codeVisionJobService.getFileAnalyses(
                repositoryId, user.getId(), severity, language, search, page, size, sortBy, sortDir
        );

        return ResponseEntity.ok(pageResult);
    }

    /**
     * GET /api/v1/repositories/{repositoryId}/code-analysis/files/{fileId}
     * Retrieves file detail with full findings list.
     */
    @GetMapping("/{repositoryId}/code-analysis/files/{fileId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CodeFileAnalysisResponse> getFileDetail(
            @PathVariable UUID repositoryId,
            @PathVariable UUID fileId,
            Principal principal) {

        UserEntity user = resolveAuthenticatedUser(principal);
        RepositoryEntity repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        verifyRepositoryOwnership(repo, user);

        CodeFileAnalysisResponse detail = codeVisionJobService.getFileAnalysisDetail(fileId);
        return ResponseEntity.ok(detail);
    }

    /**
     * POST /api/v1/repositories/{repositoryId}/code-analysis/force-rescan
     * Forces a full fresh scan bypassing incremental caching.
     */
    @PostMapping("/{repositoryId}/code-analysis/force-rescan")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CodeAnalysisRunResponse> forceRescan(
            @PathVariable UUID repositoryId,
            Principal principal) {

        return startCodeAnalysis(repositoryId, true, principal);
    }
}
