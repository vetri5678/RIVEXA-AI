package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.dto.codevision.CodeAnalysisRunResponse;
import ai.riskvision.graveyard.dto.codevision.CodeVisionSummaryResponse;
import ai.riskvision.graveyard.entity.*;
import ai.riskvision.graveyard.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CodeVisionJobServiceTest {

    @Mock
    private CodeAnalysisRunRepository runRepository;

    @Mock
    private CodeFileAnalysisRepository fileAnalysisRepository;

    @Mock
    private CodeFindingRepository findingRepository;

    @Mock
    private RepositoryEntityRepository repositoryRepository;

    @Mock
    private RepositoryMetricsEntityRepository metricsRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OAuthAccountRepository oauthAccountRepository;

    @Mock
    private RepositoryPredictionEntityRepository predictionRepository;

    @InjectMocks
    private CodeVisionJobService codeVisionJobService;

    private UUID userId;
    private UUID repoId;
    private RepositoryEntity repoEntity;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userId = UUID.randomUUID();
        repoId = UUID.randomUUID();

        repoEntity = RepositoryEntity.builder()
                .id(repoId)
                .repositoryName("RIVEXA Backend")
                .repositoryUrl("https://github.com/rivexa/backend")
                .owner("rivexa")
                .branch("main")
                .status("ACTIVE")
                .failureProbability(0.25)
                .riskLevel("LOW")
                .healthScore(85.0)
                .build();

        when(repositoryRepository.findById(repoId)).thenReturn(Optional.of(repoEntity));
    }

    @Test
    @DisplayName("startOrQueueAnalysis: creates a QUEUED run entity when no running job exists")
    void testStartOrQueueAnalysis_CreatesNewRun() {
        when(runRepository.findTopByRepositoryIdAndUserIdAndStatusOrderByCreatedAtDesc(eq(repoId), eq(userId), eq("RUNNING")))
                .thenReturn(Optional.empty());

        CodeAnalysisRunEntity createdRun = CodeAnalysisRunEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .repositoryId(repoId)
                .status("QUEUED")
                .filesDiscovered(0)
                .filesAnalyzed(0)
                .filesWithFindings(0)
                .currentlyAnalyzingFile("VALIDATING_REPOSITORY: Initializing repository tree...")
                .build();

        when(runRepository.save(any(CodeAnalysisRunEntity.class))).thenReturn(createdRun);

        CodeAnalysisRunResponse response = codeVisionJobService.startOrQueueAnalysis(repoId, userId, false);

        assertThat(response).isNotNull();
        assertThat(response.getRepositoryId()).isEqualTo(repoId);
        assertThat(response.getStatus()).isEqualTo("QUEUED");
        verify(runRepository, times(1)).save(any(CodeAnalysisRunEntity.class));
    }

    @Test
    @DisplayName("startOrQueueAnalysis: reuses existing RUNNING job if forceRescan is false")
    void testStartOrQueueAnalysis_ReusesRunningJob() {
        CodeAnalysisRunEntity runningRun = CodeAnalysisRunEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .repositoryId(repoId)
                .status("RUNNING")
                .filesDiscovered(10)
                .filesAnalyzed(4)
                .filesWithFindings(1)
                .build();

        when(runRepository.findTopByRepositoryIdAndUserIdAndStatusOrderByCreatedAtDesc(eq(repoId), eq(userId), eq("RUNNING")))
                .thenReturn(Optional.of(runningRun));

        CodeAnalysisRunResponse response = codeVisionJobService.startOrQueueAnalysis(repoId, userId, false);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("RUNNING");
        verify(runRepository, never()).save(any(CodeAnalysisRunEntity.class));
    }

    @Test
    @DisplayName("getLatestSummary: calculates severity counts from findings and returns latest run metrics")
    void testGetLatestSummary() {
        UUID runId = UUID.randomUUID();
        CodeAnalysisRunEntity completedRun = CodeAnalysisRunEntity.builder()
                .id(runId)
                .userId(userId)
                .repositoryId(repoId)
                .status("COMPLETED")
                .filesDiscovered(15)
                .filesAnalyzed(12)
                .filesWithFindings(3)
                .build();

        when(runRepository.findTopByRepositoryIdAndUserIdOrderByCreatedAtDesc(eq(repoId), eq(userId)))
                .thenReturn(Optional.of(completedRun));

        CodeFileAnalysisEntity fileAnalysis = CodeFileAnalysisEntity.builder()
                .id(UUID.randomUUID())
                .analysisRunId(runId)
                .repositoryId(repoId)
                .filePath("src/main/java/SecurityHandler.java")
                .language("Java")
                .linesOfCode(200)
                .riskScore(80)
                .severity("CRITICAL")
                .build();

        when(fileAnalysisRepository.findByAnalysisRunId(runId)).thenReturn(List.of(fileAnalysis));

        CodeFindingEntity finding1 = CodeFindingEntity.builder()
                .id(UUID.randomUUID())
                .fileAnalysisId(fileAnalysis.getId())
                .analysisRunId(runId)
                .severity("CRITICAL")
                .findingType("Security Vulnerability")
                .build();

        CodeFindingEntity finding2 = CodeFindingEntity.builder()
                .id(UUID.randomUUID())
                .fileAnalysisId(fileAnalysis.getId())
                .analysisRunId(runId)
                .severity("HIGH")
                .findingType("Performance Issue")
                .build();

        when(findingRepository.findByAnalysisRunId(runId)).thenReturn(List.of(finding1, finding2));

        CodeVisionSummaryResponse summary = codeVisionJobService.getLatestSummary(repoId, userId);

        assertThat(summary).isNotNull();
        assertThat(summary.getRepositoryId()).isEqualTo(repoId);
        assertThat(summary.getTotalFilesAnalyzed()).isEqualTo(12);
        assertThat(summary.getCriticalCount()).isEqualTo(1);
        assertThat(summary.getHighCount()).isEqualTo(1);
        assertThat(summary.getLatestRun().getStatus()).isEqualTo("COMPLETED");
    }
}
