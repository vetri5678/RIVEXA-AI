package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.client.GitHubClient;
import ai.riskvision.graveyard.dto.repository.*;
import ai.riskvision.graveyard.entity.RepositoryEntity;
import ai.riskvision.graveyard.entity.RepositoryMetricsEntity;
import ai.riskvision.graveyard.repository.RepositoryEntityRepository;
import ai.riskvision.graveyard.repository.RepositoryMetricsEntityRepository;
import ai.riskvision.graveyard.repository.RepositoryActivityEntityRepository;
import ai.riskvision.graveyard.repository.RepositoryPredictionEntityRepository;
import ai.riskvision.graveyard.util.GitHubUrlParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepositoryService {

    private final RepositoryEntityRepository repoRepository;
    private final RepositoryMetricsEntityRepository metricsRepository;
    private final RepositoryActivityEntityRepository activityRepository;
    private final RepositoryPredictionEntityRepository predictionRepository;
    private final RepositoryValidationService validationService;
    private final RepositorySyncService syncService;
    private final GitHubClient gitHubClient;

    @Transactional(readOnly = true)
    public PagedRepositoryResponse findAll(
            int page, int size, String sortBy, String sortDir,
            String search, String status, String riskLevel,
            String predictionStatus, String gitProvider, String language, String organization) {

        Sort sort = Sort.by(
                "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC,
                sortBy != null ? sortBy : "createdAt"
        );
        PageRequest pageRequest = PageRequest.of(page, size, sort);

        Page<RepositoryEntity> result = repoRepository.findAllWithFilters(
                search, status, riskLevel, predictionStatus, gitProvider, language, organization, pageRequest
        );

        List<RepositorySummaryResponse> content = result.getContent().stream()
                .map(this::toSummaryResponse)
                .collect(Collectors.toList());

        return PagedRepositoryResponse.builder()
                .content(content)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .sortBy(sortBy)
                .sortDirection(sortDir)
                .build();
    }

    @Transactional(readOnly = true)
    public RepositoryDetailResponse findById(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Repository ID must not be null");
        }
        RepositoryEntity entity = repoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Repository not found: " + id));

        RepositoryMetricsResponse metricsResponse = metricsRepository.findByRepositoryId(id)
                .map(this::toMetricsResponse)
                .orElse(null);

        RepositoryDetailResponse.RepositoryPredictionResponse latestPrediction =
                predictionRepository.findTopByRepositoryIdOrderByCreatedAtDesc(id)
                        .map(p -> RepositoryDetailResponse.RepositoryPredictionResponse.builder()
                                .id(p.getId())
                                .failureProbability(p.getFailureProbability())
                                .riskScore(p.getRiskScore())
                                .riskLevel(p.getRiskLevel())
                                .confidence(p.getConfidence())
                                .healthScore(p.getHealthScore())
                                .modelVersion(p.getModelVersion())
                                .predictionStatus(p.getPredictionStatus())
                                .featureImportanceJson(p.getFeatureImportanceJson())
                                .recommendationsJson(p.getRecommendationsJson())
                                .triggeredBy(p.getTriggeredBy())
                                .createdAt(p.getCreatedAt())
                                .build())
                        .orElse(null);

        List<RepositoryDetailResponse.RepositoryPredictionResponse> history =
                predictionRepository.findByRepositoryIdOrderByCreatedAtDesc(id, PageRequest.of(0, 20))
                        .stream()
                        .map(p -> RepositoryDetailResponse.RepositoryPredictionResponse.builder()
                                .id(p.getId())
                                .failureProbability(p.getFailureProbability())
                                .riskScore(p.getRiskScore())
                                .riskLevel(p.getRiskLevel())
                                .confidence(p.getConfidence())
                                .healthScore(p.getHealthScore())
                                .modelVersion(p.getModelVersion())
                                .predictionStatus(p.getPredictionStatus())
                                .triggeredBy(p.getTriggeredBy())
                                .createdAt(p.getCreatedAt())
                                .build())
                        .collect(Collectors.toList());

        List<RepositoryDetailResponse.RepositoryActivityResponse> activities =
                activityRepository.findByRepositoryIdOrderByCreatedAtDesc(id, PageRequest.of(0, 30))
                        .stream()
                        .map(a -> RepositoryDetailResponse.RepositoryActivityResponse.builder()
                                .id(a.getId())
                                .action(a.getAction())
                                .description(a.getDescription())
                                .actor(a.getActor())
                                .resourceType(a.getResourceType())
                                .severity(a.getSeverity())
                                .createdAt(a.getCreatedAt())
                                .build())
                        .collect(Collectors.toList());

        return toDetailResponse(entity, metricsResponse, latestPrediction, history, activities);
    }

    @Transactional
    public RepositoryResponse create(RepositoryCreateRequest request, String actor) {
        validationService.validateCreate(request);

        RepositoryEntity entity = RepositoryEntity.builder()
                .repositoryName(request.getRepositoryName())
                .description(request.getDescription())
                .organization(request.getOrganization())
                .owner(request.getOwner())
                .repositoryUrl(request.getRepositoryUrl())
                .gitProvider(request.getGitProvider())
                .branch(request.getBranch() != null ? request.getBranch() : "main")
                .technology(request.getTechnology())
                .language(request.getLanguage())
                .projectType(request.getProjectType())
                .visibility(request.getVisibility() != null ? request.getVisibility() : "PRIVATE")
                .license(request.getLicense())
                .predictionFrequency(request.getPredictionFrequency() != null ? request.getPredictionFrequency() : "WEEKLY")
                .autoPredictionEnabled(request.getAutoPredictionEnabled() != null ? request.getAutoPredictionEnabled() : true)
                .notificationsEnabled(request.getNotificationsEnabled() != null ? request.getNotificationsEnabled() : true)
                .backgroundSyncEnabled(request.getBackgroundSyncEnabled() != null ? request.getBackgroundSyncEnabled() : true)
                .reportGenerationEnabled(request.getReportGenerationEnabled() != null ? request.getReportGenerationEnabled() : false)
                .authTokenHint(request.getAuthTokenHint())
                .webhookSecret(request.getWebhookSecret())
                .status("ACTIVE")
                .predictionStatus("PENDING")
                .lifecycleStage("ACTIVE")
                .riskLevel("LOW")
                .healthScore(0.0)
                .failureProbability(0.0)
                .aiConfidence(0.0)
                .build();

        entity = repoRepository.save(entity);
        UUID entityId = Objects.requireNonNull(entity.getId(), "Generated repository ID must not be null");
        entity = repoRepository.findById(entityId).orElse(entity); // re-fetch so @CreationTimestamp fields are hydrated

        // Bootstrap empty metrics record
        RepositoryMetricsEntity metrics = RepositoryMetricsEntity.builder()
                .repositoryId(entityId)
                .build();
        metricsRepository.save(metrics);

        syncService.logActivity(entityId, "REPOSITORY_CREATED",
                "Repository '" + entity.getRepositoryName() + "' was registered", actor, "REPOSITORY", "INFO");

        log.info("Repository created: {} by {}", entityId, actor);
        return toResponse(entity);
    }

    @Transactional
    public RepositoryResponse update(UUID id, RepositoryUpdateRequest request, String actor) {
        if (id == null) {
            throw new IllegalArgumentException("Repository ID must not be null");
        }
        RepositoryEntity entity = repoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Repository not found: " + id));

        validationService.validateUpdate(id, request, entity);

        if (request.getRepositoryName() != null) entity.setRepositoryName(request.getRepositoryName());
        if (request.getDescription() != null) entity.setDescription(request.getDescription());
        if (request.getOrganization() != null) entity.setOrganization(request.getOrganization());
        if (request.getOwner() != null) entity.setOwner(request.getOwner());
        if (request.getRepositoryUrl() != null) entity.setRepositoryUrl(request.getRepositoryUrl());
        if (request.getGitProvider() != null) entity.setGitProvider(request.getGitProvider());
        if (request.getBranch() != null) entity.setBranch(request.getBranch());
        if (request.getTechnology() != null) entity.setTechnology(request.getTechnology());
        if (request.getLanguage() != null) entity.setLanguage(request.getLanguage());
        if (request.getProjectType() != null) entity.setProjectType(request.getProjectType());
        if (request.getVisibility() != null) entity.setVisibility(request.getVisibility());
        if (request.getLicense() != null) entity.setLicense(request.getLicense());
        if (request.getPredictionFrequency() != null) entity.setPredictionFrequency(request.getPredictionFrequency());
        if (request.getAutoPredictionEnabled() != null) entity.setAutoPredictionEnabled(request.getAutoPredictionEnabled());
        if (request.getNotificationsEnabled() != null) entity.setNotificationsEnabled(request.getNotificationsEnabled());
        if (request.getBackgroundSyncEnabled() != null) entity.setBackgroundSyncEnabled(request.getBackgroundSyncEnabled());
        if (request.getReportGenerationEnabled() != null) entity.setReportGenerationEnabled(request.getReportGenerationEnabled());
        if (request.getAuthTokenHint() != null) entity.setAuthTokenHint(request.getAuthTokenHint());
        if (request.getWebhookSecret() != null) entity.setWebhookSecret(request.getWebhookSecret());

        entity = repoRepository.save(entity);

        syncService.logActivity(id, "REPOSITORY_UPDATED",
                "Repository '" + entity.getRepositoryName() + "' metadata was updated", actor, "REPOSITORY", "INFO");

        return toResponse(entity);
    }

    @Transactional
    public void delete(UUID id, String actor) {
        if (id == null) {
            throw new IllegalArgumentException("Repository ID must not be null");
        }
        RepositoryEntity entity = repoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Repository not found: " + id));

        String name = entity.getRepositoryName();

        // Cascade-delete related data
        metricsRepository.deleteByRepositoryId(id);
        activityRepository.deleteByRepositoryId(id);
        predictionRepository.deleteByRepositoryId(id);
        repoRepository.delete(entity);

        log.info("Repository deleted: {} ({}) by {}", id, name, actor);
    }

    @Transactional
    public RepositoryResponse archive(UUID id, String actor) {
        if (id == null) {
            throw new IllegalArgumentException("Repository ID must not be null");
        }
        RepositoryEntity entity = repoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Repository not found: " + id));

        entity.setStatus("ARCHIVED");
        entity.setLifecycleStage("ARCHIVED");
        entity = repoRepository.save(entity);

        syncService.logActivity(id, "REPOSITORY_ARCHIVED",
                "Repository '" + entity.getRepositoryName() + "' was archived", actor, "REPOSITORY", "WARNING");

        return toResponse(entity);
    }

    @Transactional
    public RepositoryResponse restore(UUID id, String actor) {
        if (id == null) {
            throw new IllegalArgumentException("Repository ID must not be null");
        }
        RepositoryEntity entity = repoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Repository not found: " + id));

        entity.setStatus("ACTIVE");
        entity.setLifecycleStage("ACTIVE");
        entity = repoRepository.save(entity);

        syncService.logActivity(id, "REPOSITORY_RESTORED",
                "Repository '" + entity.getRepositoryName() + "' was restored from archive", actor, "REPOSITORY", "INFO");

        return toResponse(entity);
    }

    @Transactional
    public RepositoryResponse duplicate(UUID id, String actor) {
        if (id == null) {
            throw new IllegalArgumentException("Repository ID must not be null");
        }
        RepositoryEntity source = repoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Repository not found: " + id));

        RepositoryEntity copy = RepositoryEntity.builder()
                .repositoryName(source.getRepositoryName() + " (Copy)")
                .description(source.getDescription())
                .organization(source.getOrganization())
                .owner(source.getOwner())
                .repositoryUrl(source.getRepositoryUrl())
                .gitProvider(source.getGitProvider())
                .branch(source.getBranch())
                .technology(source.getTechnology())
                .language(source.getLanguage())
                .projectType(source.getProjectType())
                .visibility(source.getVisibility())
                .license(source.getLicense())
                .predictionFrequency(source.getPredictionFrequency())
                .autoPredictionEnabled(source.getAutoPredictionEnabled())
                .notificationsEnabled(source.getNotificationsEnabled())
                .backgroundSyncEnabled(source.getBackgroundSyncEnabled())
                .reportGenerationEnabled(source.getReportGenerationEnabled())
                .status("ACTIVE")
                .predictionStatus("PENDING")
                .lifecycleStage("ACTIVE")
                .riskLevel("LOW")
                .healthScore(0.0)
                .failureProbability(0.0)
                .aiConfidence(0.0)
                .build();

        copy = repoRepository.save(copy);
        UUID copyId = Objects.requireNonNull(copy.getId(), "Generated copy repository ID must not be null");

        metricsRepository.save(RepositoryMetricsEntity.builder().repositoryId(copyId).build());

        syncService.logActivity(copyId, "REPOSITORY_DUPLICATED",
                "Repository duplicated from '" + source.getRepositoryName() + "'", actor, "REPOSITORY", "INFO");

        return toResponse(copy);
    }

    @Transactional(readOnly = true)
    public RepositoryMetricsResponse getMetrics(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Repository ID must not be null");
        }
        repoRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Repository not found: " + id));
        return metricsRepository.findByRepositoryId(id)
                .map(this::toMetricsResponse)
                .orElse(RepositoryMetricsResponse.builder().repositoryId(id).build());
    }

    // ─── GitHub-native helper ────────────────────────────────────────────────────

    /**
     * Finds an existing repository by its GitHub URL, or creates a new one by fetching
     * live metadata from the GitHub REST API. Used by the predict-by-url endpoint.
     *
     * <p>Flow:
     * <ol>
     *   <li>Parse the URL to extract owner/repo.</li>
     *   <li>Look up the repository by its normalized URL in the database.</li>
     *   <li>If found, return the existing entity (to reuse prediction history and metrics).</li>
     *   <li>If not found, fetch metadata from GitHub API, persist a new entity, create an
     *       empty metrics record, and trigger an initial sync to populate metrics.</li>
     * </ol>
     *
     * @param githubUrl the full GitHub URL, e.g. {@code https://github.com/owner/repo}
     * @param actor     the user or system principal triggering this action
     * @return the existing or newly created {@link RepositoryEntity}
     */
    @Transactional
    public RepositoryEntity findOrCreateByGithubUrl(String githubUrl, String actor) {
        GitHubUrlParser.ParsedRepo parsed = GitHubUrlParser.parse(githubUrl);
        String owner = parsed.getOwner();
        String repo  = parsed.getRepo();
        // Normalise the URL for consistent DB lookups (strip trailing slashes / .git)
        String normalizedUrl = "https://github.com/" + owner + "/" + repo;

        log.info("[RepositoryService] findOrCreateByGithubUrl — owner={} repo={} actor={}", owner, repo, actor);

        // ── Path A: existing record ──────────────────────────────────────────────
        return repoRepository.findByRepositoryUrl(normalizedUrl).orElseGet(() -> {
            log.info("[RepositoryService] Repository not in DB — fetching from GitHub API: {}/{}", owner, repo);

            // ── Path B: fetch from GitHub and create ─────────────────────────────
            String repoName    = repo;
            String description = null;
            String language    = null;
            String visibility  = "PUBLIC";
            String license     = null;
            String defaultBranch = "main";
            String ownerLogin  = owner;

            try {
                Map<String, Object> meta = gitHubClient.getRepositoryMetadata(owner, repo);
                if (meta != null) {
                    if (meta.get("name") != null)        repoName     = (String) meta.get("name");
                    if (meta.get("description") != null) description  = (String) meta.get("description");
                    if (meta.get("language") != null)    language     = (String) meta.get("language");
                    if (meta.get("private") instanceof Boolean p) visibility = p ? "PRIVATE" : "PUBLIC";
                    if (meta.get("default_branch") != null) defaultBranch = (String) meta.get("default_branch");
                    if (meta.get("license") instanceof Map<?,?> lic && lic.get("name") != null)
                        license = (String) lic.get("name");
                    if (meta.get("owner") instanceof Map<?,?> ownerMap && ownerMap.get("login") != null)
                        ownerLogin = (String) ownerMap.get("login");
                }
            } catch (Exception ex) {
                log.warn("[RepositoryService] GitHub API unavailable for {}/{} — using defaults. Error: {}",
                        owner, repo, ex.getMessage());
            }

            RepositoryEntity entity = RepositoryEntity.builder()
                    .repositoryName(repoName)
                    .description(description)
                    .organization(ownerLogin)
                    .owner(ownerLogin)
                    .repositoryUrl(normalizedUrl)
                    .gitProvider("GITHUB")
                    .branch(defaultBranch)
                    .language(language)
                    .visibility(visibility)
                    .license(license)
                    .status("ACTIVE")
                    .predictionStatus("PENDING")
                    .lifecycleStage("ACTIVE")
                    .riskLevel("LOW")
                    .healthScore(0.0)
                    .failureProbability(0.0)
                    .aiConfidence(0.0)
                    .predictionFrequency("MANUAL")
                    .autoPredictionEnabled(false)
                    .notificationsEnabled(true)
                    .backgroundSyncEnabled(true)
                    .reportGenerationEnabled(false)
                    .build();

            entity = repoRepository.save(entity);
            UUID entityId = Objects.requireNonNull(entity.getId(), "Generated repository ID must not be null");
            entity = repoRepository.findById(entityId).orElse(entity);

            // Bootstrap empty metrics record
            metricsRepository.save(RepositoryMetricsEntity.builder().repositoryId(entityId).build());

            // Trigger sync to populate metrics from GitHub API
            try {
                syncService.syncRepository(entityId, actor);
                entity = repoRepository.findById(entityId).orElse(entity);
            } catch (Exception ex) {
                log.warn("[RepositoryService] Initial sync failed for new repo {} — prediction will use defaults. Error: {}",
                        entityId, ex.getMessage());
            }

            syncService.logActivity(entityId, "REPOSITORY_CREATED_VIA_URL",
                    "Repository '" + entity.getRepositoryName() + "' registered via GitHub URL by " + actor,
                    actor, "REPOSITORY", "INFO");

            log.info("[RepositoryService] New repository created from GitHub URL: {} → {}", normalizedUrl, entityId);
            return entity;
        });
    }

    // ─── Mapping helpers ────────────────────────────────────────────────────────

    public RepositoryResponse toResponse(RepositoryEntity e) {
        return RepositoryResponse.builder()
                .id(e.getId()).repositoryName(e.getRepositoryName()).description(e.getDescription())
                .organization(e.getOrganization()).owner(e.getOwner()).repositoryUrl(e.getRepositoryUrl())
                .gitProvider(e.getGitProvider()).branch(e.getBranch()).technology(e.getTechnology())
                .language(e.getLanguage()).projectType(e.getProjectType()).visibility(e.getVisibility())
                .license(e.getLicense()).healthScore(e.getHealthScore()).failureProbability(e.getFailureProbability())
                .predictionStatus(e.getPredictionStatus()).lifecycleStage(e.getLifecycleStage())
                .status(e.getStatus()).riskLevel(e.getRiskLevel()).aiConfidence(e.getAiConfidence())
                .contributors(e.getContributors()).openIssues(e.getOpenIssues())
                .lastCommitDate(e.getLastCommitDate()).lastSyncDate(e.getLastSyncDate())
                .predictionFrequency(e.getPredictionFrequency()).autoPredictionEnabled(e.getAutoPredictionEnabled())
                .notificationsEnabled(e.getNotificationsEnabled()).backgroundSyncEnabled(e.getBackgroundSyncEnabled())
                .reportGenerationEnabled(e.getReportGenerationEnabled())
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt()).build();
    }

    private RepositorySummaryResponse toSummaryResponse(RepositoryEntity e) {
        return RepositorySummaryResponse.builder()
                .id(e.getId()).repositoryName(e.getRepositoryName()).organization(e.getOrganization())
                .description(e.getDescription()).technology(e.getTechnology()).language(e.getLanguage())
                .repositoryUrl(e.getRepositoryUrl()).gitProvider(e.getGitProvider()).branch(e.getBranch())
                .status(e.getStatus()).healthScore(e.getHealthScore()).failureProbability(e.getFailureProbability())
                .predictionStatus(e.getPredictionStatus()).contributors(e.getContributors())
                .openIssues(e.getOpenIssues()).lastCommitDate(e.getLastCommitDate())
                .lastSyncDate(e.getLastSyncDate()).lifecycleStage(e.getLifecycleStage())
                .aiConfidence(e.getAiConfidence()).riskLevel(e.getRiskLevel()).createdAt(e.getCreatedAt()).build();
    }

    private RepositoryMetricsResponse toMetricsResponse(RepositoryMetricsEntity m) {
        return RepositoryMetricsResponse.builder()
                .id(m.getId()).repositoryId(m.getRepositoryId()).commitCount(m.getCommitCount())
                .commitFrequency(m.getCommitFrequency()).pullRequests(m.getPullRequests())
                .mergedPullRequests(m.getMergedPullRequests()).failedPullRequests(m.getFailedPullRequests())
                .contributors(m.getContributors()).activeContributors(m.getActiveContributors())
                .inactiveDays(m.getInactiveDays()).openIssues(m.getOpenIssues()).closedIssues(m.getClosedIssues())
                .codeCoverage(m.getCodeCoverage()).documentationScore(m.getDocumentationScore())
                .buildSuccessRate(m.getBuildSuccessRate()).cyclomaticComplexity(m.getCyclomaticComplexity())
                .technicalDebt(m.getTechnicalDebt()).busFactor(m.getBusFactor()).velocity(m.getVelocity())
                .updatedAt(m.getUpdatedAt()).build();
    }

    private RepositoryDetailResponse toDetailResponse(
            RepositoryEntity e, RepositoryMetricsResponse metrics,
            RepositoryDetailResponse.RepositoryPredictionResponse latestPrediction,
            List<RepositoryDetailResponse.RepositoryPredictionResponse> history,
            List<RepositoryDetailResponse.RepositoryActivityResponse> activities) {
        return RepositoryDetailResponse.builder()
                .id(e.getId()).repositoryName(e.getRepositoryName()).description(e.getDescription())
                .organization(e.getOrganization()).owner(e.getOwner()).repositoryUrl(e.getRepositoryUrl())
                .gitProvider(e.getGitProvider()).branch(e.getBranch()).technology(e.getTechnology())
                .language(e.getLanguage()).projectType(e.getProjectType()).visibility(e.getVisibility())
                .license(e.getLicense()).healthScore(e.getHealthScore()).failureProbability(e.getFailureProbability())
                .predictionStatus(e.getPredictionStatus()).lifecycleStage(e.getLifecycleStage())
                .status(e.getStatus()).riskLevel(e.getRiskLevel()).aiConfidence(e.getAiConfidence())
                .contributors(e.getContributors()).openIssues(e.getOpenIssues())
                .lastCommitDate(e.getLastCommitDate()).lastSyncDate(e.getLastSyncDate())
                .predictionFrequency(e.getPredictionFrequency()).autoPredictionEnabled(e.getAutoPredictionEnabled())
                .notificationsEnabled(e.getNotificationsEnabled()).backgroundSyncEnabled(e.getBackgroundSyncEnabled())
                .reportGenerationEnabled(e.getReportGenerationEnabled())
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt())
                .metrics(metrics).latestPrediction(latestPrediction)
                .predictionHistory(history).recentActivities(activities).build();
    }
}
