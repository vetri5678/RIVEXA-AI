package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.client.GitHubClient;
import ai.riskvision.graveyard.dto.codevision.*;
import ai.riskvision.graveyard.entity.CodeAnalysisRunEntity;
import ai.riskvision.graveyard.entity.CodeFileAnalysisEntity;
import ai.riskvision.graveyard.entity.CodeFindingEntity;
import ai.riskvision.graveyard.entity.OAuthAccountEntity;
import ai.riskvision.graveyard.entity.RepositoryEntity;
import ai.riskvision.graveyard.entity.UserEntity;
import ai.riskvision.graveyard.repository.CodeAnalysisRunRepository;
import ai.riskvision.graveyard.repository.CodeFileAnalysisRepository;
import ai.riskvision.graveyard.repository.CodeFindingRepository;
import ai.riskvision.graveyard.repository.OAuthAccountRepository;
import ai.riskvision.graveyard.repository.RepositoryEntityRepository;
import ai.riskvision.graveyard.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpMethod;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CodeVisionJobService {

    private final CodeAnalysisRunRepository runRepository;
    private final CodeFileAnalysisRepository fileAnalysisRepository;
    private final CodeFindingRepository findingRepository;
    private final RepositoryEntityRepository repositoryRepository;
    private final UserRepository userRepository;
    private final OAuthAccountRepository oauthAccountRepository;
    private final GitHubClient gitHubClient;
    private final CodeVisionAnalysisEngine analysisEngine;
    private final RepoPredictionService repoPredictionService;
    private final RepositoryPredictionEntityRepository predictionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String resolveUserToken(UUID userId) {
        if (userId == null) return null;
        try {
            Optional<UserEntity> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                Optional<OAuthAccountEntity> oauthOpt = oauthAccountRepository.findByUserAndProvider(userOpt.get(), "github");
                if (oauthOpt.isPresent() && oauthOpt.get().getAccessToken() != null && !oauthOpt.get().getAccessToken().trim().isEmpty()) {
                    return oauthOpt.get().getAccessToken().trim();
                }
            }
        } catch (Exception ex) {
            log.warn("[CodeVision] Failed to resolve user GitHub OAuth token for userId={}: {}", userId, ex.getMessage());
        }
        return null;
    }

    /**
     * Starts or queues a Code Vision AI analysis for a user's repository.
     */
    @Transactional
    public CodeAnalysisRunResponse startOrQueueAnalysis(UUID repositoryId, UUID userId, boolean forceRescan) {
        RepositoryEntity repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        // Check if an analysis is currently running
        Optional<CodeAnalysisRunEntity> runningOpt = runRepository.findTopByRepositoryIdAndUserIdAndStatusOrderByCreatedAtDesc(repositoryId, userId, "RUNNING");
        if (runningOpt.isPresent()) {
            return mapToRunResponse(runningOpt.get());
        }

        CodeAnalysisRunEntity run = CodeAnalysisRunEntity.builder()
                .userId(userId)
                .repositoryId(repositoryId)
                .status("QUEUED")
                .filesDiscovered(0)
                .filesAnalyzed(0)
                .filesWithFindings(0)
                .currentlyAnalyzingFile("Initializing Repository Tree...")
                .build();

        run = runRepository.save(run);

        // Execute background processing
        executeAsyncAnalysis(run.getId(), repo, userId, forceRescan);

        return mapToRunResponse(run);
    }

    @Async
    public void executeAsyncAnalysis(UUID runId, RepositoryEntity repo, UUID userId, boolean forceRescan) {
        log.info("[CodeVision] Loading repositories for user: {}", userId);
        log.info("[CodeVision] Repository selected: id={}, name={}", repo.getId(), repo.getRepositoryName());
        log.info("[CodeVision] Starting analysis for runId={}", runId);

        CodeAnalysisRunEntity run = runRepository.findById(runId).orElse(null);
        if (run == null) return;

        run.setStatus("RUNNING");
        run.setStartedAt(Instant.now());
        run.setCurrentlyAnalyzingFile("Fetching GitHub Tree...");
        runRepository.save(run);

        try {
            String owner = extractOwner(repo);
            String name = extractRepoName(repo);
            String userToken = resolveUserToken(userId);

            // Dynamically resolve default branch
            String branch = repo.getBranch() != null ? repo.getBranch() : "main";
            try {
                Map<String, Object> repoMetadata = gitHubClient.executeRequest(
                        "/repos/" + owner + "/" + name,
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<Map<String, Object>>() {},
                        owner, name, "GITHUB_REPO_METADATA", "Fetch metadata for branch resolution",
                        userToken
                );
                if (repoMetadata != null && repoMetadata.get("default_branch") != null) {
                    branch = String.valueOf(repoMetadata.get("default_branch"));
                }
            } catch (Exception ex) {
                log.warn("[CodeVision] Branch metadata lookup failed for {}/{}. Using default: {}", owner, name, branch);
            }

            log.info("[CodeVision] Fetching repository tree for owner={} name={} branch={}", owner, name, branch);

            // 1. Fetch Repository Tree recursively from GitHub REST API
            String treeEndpoint = "/repos/" + owner + "/" + name + "/git/trees/" + branch + "?recursive=1";
            Map<String, Object> treeResponse = null;
            try {
                treeResponse = gitHubClient.executeRequest(
                        treeEndpoint,
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<Map<String, Object>>() {},
                        owner, name, "GITHUB_FETCH_TREE", "Fetch git tree for Code Vision AI",
                        userToken
                );
            } catch (Exception treeEx) {
                if (!"main".equals(branch)) {
                    log.warn("[CodeVision] Tree fetch failed on branch '{}'. Trying fallback 'main'...", branch);
                    treeEndpoint = "/repos/" + owner + "/" + name + "/git/trees/main?recursive=1";
                    treeResponse = gitHubClient.executeRequest(
                            treeEndpoint,
                            HttpMethod.GET,
                            null,
                            new ParameterizedTypeReference<Map<String, Object>>() {},
                            owner, name, "GITHUB_FETCH_TREE", "Fetch git tree fallback main",
                            userToken
                    );
                } else {
                    throw treeEx;
                }
            }

            List<Map<String, Object>> treeItems = (List<Map<String, Object>>) treeResponse.getOrDefault("tree", Collections.emptyList());
            int totalDiscoveredBlobs = 0;
            List<Map<String, Object>> supportedFiles = new ArrayList<>();

            for (Map<String, Object> item : treeItems) {
                String type = String.valueOf(item.get("type"));
                String path = String.valueOf(item.get("path"));
                long size = item.containsKey("size") && item.get("size") instanceof Number ? ((Number) item.get("size")).longValue() : 0L;

                if ("blob".equals(type)) {
                    totalDiscoveredBlobs++;
                    if (analysisEngine.shouldAnalyzeFile(path, size)) {
                        supportedFiles.add(item);
                    }
                }
            }

            run.setFilesDiscovered(supportedFiles.size());
            runRepository.save(run);

            log.info("[CodeVision] Files discovered: {}", totalDiscoveredBlobs);
            log.info("[CodeVision] Supported source files: {}", supportedFiles.size());
            log.info("[CodeVision] Files excluded: {}", totalDiscoveredBlobs - supportedFiles.size());
            log.info("[CodeVision] Starting batch analysis");

            int filesAnalyzed = 0;
            int filesWithFindings = 0;

            for (Map<String, Object> fileItem : supportedFiles) {
                String filePath = String.valueOf(fileItem.get("path"));
                String sha = fileItem.containsKey("sha") ? String.valueOf(fileItem.get("sha")) : null;
                run.setCurrentlyAnalyzingFile(filePath);
                runRepository.save(run);

                try {
                    String content = null;
                    // Try fetching content via REST API
                    try {
                        String rawEndpoint = "/repos/" + owner + "/" + name + "/contents/" + filePath + "?ref=" + branch;
                        Map<String, Object> contentResponse = gitHubClient.executeRequest(
                                rawEndpoint,
                                HttpMethod.GET,
                                null,
                                new ParameterizedTypeReference<Map<String, Object>>() {},
                                owner, name, "GITHUB_FETCH_FILE", "Fetch source file content: " + filePath,
                                userToken
                        );
                        content = extractContent(contentResponse);
                    } catch (Exception contentEx) {
                        log.debug("[CodeVision] Content fetch failed for {}: {}. Attempting blob endpoint fallback...", filePath, contentEx.getMessage());
                    }

                    // Blob endpoint fallback if contents endpoint was empty or failed (e.g. files > 1MB)
                    if ((content == null || content.isBlank()) && sha != null && !sha.isBlank()) {
                        try {
                            String blobEndpoint = "/repos/" + owner + "/" + name + "/git/blobs/" + sha;
                            Map<String, Object> blobResponse = gitHubClient.executeRequest(
                                    blobEndpoint,
                                    HttpMethod.GET,
                                    null,
                                    new ParameterizedTypeReference<Map<String, Object>>() {},
                                    owner, name, "GITHUB_FETCH_BLOB", "Fetch blob content: " + sha,
                                    userToken
                            );
                            content = extractContent(blobResponse);
                        } catch (Exception blobEx) {
                            log.warn("[CodeVision] Blob fetch also failed for file {}: {}", filePath, blobEx.getMessage());
                        }
                    }

                    if (content == null || content.isBlank()) {
                        log.warn("[CodeVision] Content empty or unretrievable for file {}", filePath);
                        continue;
                    }

                    String fileHash = analysisEngine.computeSHA256(content);

                    // Check for existing cached file analysis if forceRescan is false
                    CodeFileAnalysisEntity fileAnalysis = null;
                    if (!forceRescan) {
                        Optional<CodeFileAnalysisEntity> cachedOpt = fileAnalysisRepository.findTopByRepositoryIdAndFilePathOrderByAnalyzedAtDesc(repo.getId(), filePath);
                        if (cachedOpt.isPresent() && fileHash.equals(cachedOpt.get().getFileHash())) {
                            CodeFileAnalysisEntity cached = cachedOpt.get();
                            fileAnalysis = CodeFileAnalysisEntity.builder()
                                    .analysisRunId(runId)
                                    .repositoryId(repo.getId())
                                    .filePath(filePath)
                                    .fileHash(fileHash)
                                    .language(cached.getLanguage())
                                    .linesOfCode(cached.getLinesOfCode())
                                    .riskScore(cached.getRiskScore())
                                    .severity(cached.getSeverity())
                                    .confidence(cached.getConfidence())
                                    .analysisType(cached.getAnalysisType())
                                    .metricsJson(cached.getMetricsJson())
                                    .status("ANALYZED")
                                    .build();
                            fileAnalysis = fileAnalysisRepository.save(fileAnalysis);

                            // Copy existing findings
                            List<CodeFindingEntity> cachedFindings = findingRepository.findByFileAnalysisIdOrderBySeverityDesc(cached.getId());
                            for (CodeFindingEntity cf : cachedFindings) {
                                CodeFindingEntity copy = CodeFindingEntity.builder()
                                        .fileAnalysisId(fileAnalysis.getId())
                                        .analysisRunId(runId)
                                        .findingType(cf.getFindingType())
                                        .severity(cf.getSeverity())
                                        .confidence(cf.getConfidence())
                                        .symbolName(cf.getSymbolName())
                                        .startLine(cf.getStartLine())
                                        .endLine(cf.getEndLine())
                                        .title(cf.getTitle())
                                        .description(cf.getDescription())
                                        .evidence(cf.getEvidence())
                                        .recommendation(cf.getRecommendation())
                                        .analysisSource("STATIC")
                                        .build();
                                findingRepository.save(copy);
                            }
                        }
                    }

                    if (fileAnalysis == null) {
                        UUID fileAnalysisId = UUID.randomUUID();
                        CodeVisionAnalysisEngine.FileAnalysisResult res = analysisEngine.analyzeSourceFile(filePath, content, runId, fileAnalysisId);

                        fileAnalysis = CodeFileAnalysisEntity.builder()
                                .id(fileAnalysisId)
                                .analysisRunId(runId)
                                .repositoryId(repo.getId())
                                .filePath(filePath)
                                .fileHash(fileHash)
                                .language(res.getLanguage())
                                .linesOfCode(res.getLinesOfCode())
                                .riskScore(res.getRiskScore())
                                .severity(res.getSeverity())
                                .confidence(res.getConfidence())
                                .analysisType(res.getAnalysisType())
                                .metricsJson(objectMapper.writeValueAsString(res.getMetrics()))
                                .status("ANALYZED")
                                .build();
                        fileAnalysis = fileAnalysisRepository.save(fileAnalysis);

                        if (res.getFindings() != null && !res.getFindings().isEmpty()) {
                            for (CodeFindingEntity finding : res.getFindings()) {
                                finding.setFileAnalysisId(fileAnalysis.getId());
                                finding.setAnalysisRunId(runId);
                                findingRepository.save(finding);
                            }
                        }
                    }

                    filesAnalyzed++;
                    long findingsCount = findingRepository.countByFileAnalysisId(fileAnalysis.getId());
                    if (findingsCount > 0) {
                        filesWithFindings++;
                    }

                    log.info("[CodeVision] File analyzed: {}", filePath);
                    log.info("[CodeVision] Findings detected: {}", findingsCount);
                    log.info("[CodeVision] Analysis progress: {}/{}", filesAnalyzed, supportedFiles.size());

                    run.setFilesAnalyzed(filesAnalyzed);
                    run.setFilesWithFindings(filesWithFindings);
                    runRepository.save(run);

                } catch (Exception fileEx) {
                    log.warn("[CodeVision] Failed to analyze file {}: {}", filePath, fileEx.getMessage());
                }
            }

            }

            // Execute XGBoost Model Risk Inference & SHAP explanation pipeline
            try {
                run.setCurrentlyAnalyzingFile("Running XGBoost Risk Model & SHAP Explanation...");
                runRepository.save(run);
                log.info("[CodeVision] Triggering XGBoost prediction model pipeline for repositoryId={}", repo.getId());
                repoPredictionService.runPrediction(repo.getId(), "CODE_VISION_AI");
                log.info("[CodeVision] XGBoost prediction & SHAP explanation completed for repositoryId={}", repo.getId());
            } catch (Exception predEx) {
                log.warn("[CodeVision] XGBoost model pipeline step warning for repositoryId={}: {}", repo.getId(), predEx.getMessage());
            }

            run.setStatus("COMPLETED");
            run.setCompletedAt(Instant.now());
            run.setCurrentlyAnalyzingFile(null);
            runRepository.save(run);

            log.info("[CodeVision] Analysis completed successfully for runId={} analyzed {} files with {} files containing findings", runId, filesAnalyzed, filesWithFindings);

        } catch (Exception ex) {
            log.error("[CodeVision] Code Vision AI runId={} failed: {}", runId, ex.getMessage(), ex);
            run.setStatus("FAILED");
            run.setErrorMessage(ex.getMessage());
            run.setCompletedAt(Instant.now());
            run.setCurrentlyAnalyzingFile(null);
            runRepository.save(run);
        }
    }

    public CodeVisionSummaryResponse getLatestSummary(UUID repositoryId, UUID userId) {
        Optional<CodeAnalysisRunEntity> runOpt = runRepository.findTopByRepositoryIdAndUserIdOrderByCreatedAtDesc(repositoryId, userId);
        if (runOpt.isEmpty()) {
            return CodeVisionSummaryResponse.builder()
                    .repositoryId(repositoryId)
                    .latestRun(null)
                    .totalFilesDiscovered(0)
                    .totalFilesAnalyzed(0)
                    .filesWithFindings(0)
                    .criticalCount(0)
                    .highCount(0)
                    .mediumCount(0)
                    .lowCount(0)
                    .languageBreakdown(Collections.emptyMap())
                    .findingTypeBreakdown(Collections.emptyMap())
                    .build();
        }

        CodeAnalysisRunEntity run = runOpt.get();
        List<CodeFileAnalysisEntity> fileAnalyses = fileAnalysisRepository.findByAnalysisRunId(run.getId());
        List<CodeFindingEntity> findings = findingRepository.findByAnalysisRunId(run.getId());

        long critical = fileAnalyses.stream().filter(f -> "CRITICAL".equals(f.getSeverity())).count();
        long high = fileAnalyses.stream().filter(f -> "HIGH".equals(f.getSeverity())).count();
        long medium = fileAnalyses.stream().filter(f -> "MEDIUM".equals(f.getSeverity())).count();
        long low = fileAnalyses.stream().filter(f -> "LOW".equals(f.getSeverity())).count();

        Map<String, Long> langMap = fileAnalyses.stream()
                .collect(Collectors.groupingBy(f -> f.getLanguage() != null ? f.getLanguage() : "Other", Collectors.counting()));

        Map<String, Long> findingTypeMap = findings.stream()
                .collect(Collectors.groupingBy(CodeFindingEntity::getFindingType, Collectors.counting()));

        RepositoryEntity repo = repositoryRepository.findById(repositoryId).orElse(null);
        Double failureProb = repo != null ? repo.getFailureProbability() : null;
        Integer riskScore = failureProb != null ? (int) Math.round(failureProb * 100) : null;
        String riskLevel = repo != null ? repo.getRiskLevel() : null;
        Double healthScore = repo != null ? repo.getHealthScore() : null;
        Double aiConfidence = repo != null ? repo.getAiConfidence() : null;

        String modelVersion = "xgboost-v1.0";
        Object featureImportance = null;

        if (repo != null) {
            Optional<RepositoryPredictionEntity> latestPredOpt = predictionRepository.findTopByRepositoryIdOrderByCreatedAtDesc(repositoryId);
            if (latestPredOpt.isPresent()) {
                RepositoryPredictionEntity pred = latestPredOpt.get();
                if (pred.getModelVersion() != null) modelVersion = pred.getModelVersion();
                if (pred.getFeatureImportanceJson() != null) {
                    try {
                        featureImportance = objectMapper.readValue(pred.getFeatureImportanceJson(), Object.class);
                    } catch (Exception ignored) {}
                }
            }
        }

        return CodeVisionSummaryResponse.builder()
                .repositoryId(repositoryId)
                .latestRun(mapToRunResponse(run))
                .totalFilesDiscovered(run.getFilesDiscovered())
                .totalFilesAnalyzed(run.getFilesAnalyzed())
                .filesWithFindings(run.getFilesWithFindings())
                .criticalCount(critical)
                .highCount(high)
                .mediumCount(medium)
                .lowCount(low)
                .failureProbability(failureProb)
                .riskScore(riskScore)
                .riskLevel(riskLevel)
                .healthScore(healthScore)
                .aiConfidence(aiConfidence)
                .modelVersion(modelVersion)
                .featureImportance(featureImportance)
                .languageBreakdown(langMap)
                .findingTypeBreakdown(findingTypeMap)
                .build();
    }

    public Page<CodeFileAnalysisResponse> getFileAnalyses(UUID repositoryId, UUID userId, String severity, String language, String search, int page, int size, String sortBy, String sortDir) {
        Optional<CodeAnalysisRunEntity> runOpt = runRepository.findTopByRepositoryIdAndUserIdOrderByCreatedAtDesc(repositoryId, userId);
        if (runOpt.isEmpty()) {
            return Page.empty();
        }
        CodeAnalysisRunEntity run = runOpt.get();

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String prop = "riskScore".equals(sortBy) ? "riskScore" : ("linesOfCode".equals(sortBy) ? "linesOfCode" : "analyzedAt");
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, prop));

        Page<CodeFileAnalysisEntity> pageResult = fileAnalysisRepository.findByRunIdWithFilters(
                run.getId(),
                (severity != null && !severity.isBlank()) ? severity.toUpperCase() : null,
                (language != null && !language.isBlank()) ? language : null,
                (search != null && !search.isBlank()) ? search.trim() : null,
                pageable
        );

        return pageResult.map(this::mapToFileResponse);
    }

    public CodeFileAnalysisResponse getFileAnalysisDetail(UUID fileId) {
        CodeFileAnalysisEntity entity = fileAnalysisRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File analysis record not found: " + fileId));
        return mapToFileResponseWithFindings(entity);
    }

    private CodeAnalysisRunResponse mapToRunResponse(CodeAnalysisRunEntity run) {
        return CodeAnalysisRunResponse.builder()
                .id(run.getId())
                .userId(run.getUserId())
                .repositoryId(run.getRepositoryId())
                .status(run.getStatus())
                .startedAt(run.getStartedAt())
                .completedAt(run.getCompletedAt())
                .filesDiscovered(run.getFilesDiscovered())
                .filesAnalyzed(run.getFilesAnalyzed())
                .filesWithFindings(run.getFilesWithFindings())
                .currentlyAnalyzingFile(run.getCurrentlyAnalyzingFile())
                .errorMessage(run.getErrorMessage())
                .createdAt(run.getCreatedAt())
                .build();
    }

    private CodeFileAnalysisResponse mapToFileResponse(CodeFileAnalysisEntity f) {
        long findingCount = findingRepository.countByFileAnalysisId(f.getId());
        Map<String, Object> metrics = parseMetricsJson(f.getMetricsJson());

        return CodeFileAnalysisResponse.builder()
                .id(f.getId())
                .analysisRunId(f.getAnalysisRunId())
                .repositoryId(f.getRepositoryId())
                .filePath(f.getFilePath())
                .fileHash(f.getFileHash())
                .language(f.getLanguage())
                .linesOfCode(f.getLinesOfCode())
                .riskScore(f.getRiskScore())
                .severity(f.getSeverity())
                .confidence(f.getConfidence())
                .analysisType(f.getAnalysisType())
                .metrics(metrics)
                .status(f.getStatus())
                .analyzedAt(f.getAnalyzedAt())
                .findingCount(findingCount)
                .build();
    }

    private CodeFileAnalysisResponse mapToFileResponseWithFindings(CodeFileAnalysisEntity f) {
        CodeFileAnalysisResponse resp = mapToFileResponse(f);
        List<CodeFindingEntity> findings = findingRepository.findByFileAnalysisIdOrderBySeverityDesc(f.getId());
        resp.setFindings(findings.stream().map(this::mapToFindingResponse).collect(Collectors.toList()));
        return resp;
    }

    private CodeFindingResponse mapToFindingResponse(CodeFindingEntity f) {
        return CodeFindingResponse.builder()
                .id(f.getId())
                .fileAnalysisId(f.getFileAnalysisId())
                .analysisRunId(f.getAnalysisRunId())
                .findingType(f.getFindingType())
                .severity(f.getSeverity())
                .confidence(f.getConfidence())
                .symbolName(f.getSymbolName())
                .startLine(f.getStartLine())
                .endLine(f.getEndLine())
                .title(f.getTitle())
                .description(f.getDescription())
                .evidence(f.getEvidence())
                .recommendation(f.getRecommendation())
                .analysisSource(f.getAnalysisSource())
                .createdAt(f.getCreatedAt())
                .build();
    }

    private Map<String, Object> parseMetricsJson(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private String extractOwner(RepositoryEntity repo) {
        if (repo.getOwner() != null && !repo.getOwner().isBlank()) return repo.getOwner();
        if (repo.getOrganization() != null && !repo.getOrganization().isBlank()) return repo.getOrganization();
        if (repo.getRepositoryUrl() != null && repo.getRepositoryUrl().contains("github.com/")) {
            String[] parts = repo.getRepositoryUrl().split("github.com/")[1].split("/");
            if (parts.length >= 1) return parts[0];
        }
        return "owner";
    }

    private String extractRepoName(RepositoryEntity repo) {
        if (repo.getRepositoryName() != null && !repo.getRepositoryName().isBlank()) return repo.getRepositoryName();
        if (repo.getRepositoryUrl() != null && repo.getRepositoryUrl().contains("github.com/")) {
            String[] parts = repo.getRepositoryUrl().split("github.com/")[1].split("/");
            if (parts.length >= 2) return parts[1].replace(".git", "");
        }
        return "repo";
    }

    private String extractContent(Map<String, Object> response) {
        if (response == null) return null;
        if (response.containsKey("content") && response.get("content") != null) {
            String rawBase64 = String.valueOf(response.get("content")).replaceAll("\\s+", "");
            try {
                byte[] decoded = Base64.getDecoder().decode(rawBase64);
                return new String(decoded, StandardCharsets.UTF_8);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
