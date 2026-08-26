package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.client.OpenRouterClient;
import ai.riskvision.graveyard.entity.RepositoryEntity;
import ai.riskvision.graveyard.entity.RepositoryPredictionEntity;
import ai.riskvision.graveyard.repository.AuditLogRepository;
import ai.riskvision.graveyard.repository.ProjectRepository;
import ai.riskvision.graveyard.repository.RepositoryEntityRepository;
import ai.riskvision.graveyard.repository.RepositoryPredictionEntityRepository;
import ai.riskvision.graveyard.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReportGenerationTest {

    @Mock
    private OpenRouterClient openRouterClient;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private RepositoryEntityRepository repositoryEntityRepository;

    @Mock
    private RepositoryPredictionEntityRepository predictionRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private N8nWebhookService n8nWebhookService;

    private ObjectMapper objectMapper;
    private PdfReportService pdfReportService;
    private ExcelReportService excelReportService;
    private ReportGenerationService reportGenerationService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        pdfReportService = new PdfReportService(openRouterClient, objectMapper);
        excelReportService = new ExcelReportService(objectMapper);
        reportGenerationService = new ReportGenerationService(
                userRepository,
                projectRepository,
                repositoryEntityRepository,
                predictionRepository,
                auditLogRepository,
                pdfReportService,
                excelReportService,
                objectMapper,
                n8nWebhookService
        );
    }

    @Test
    @DisplayName("PDF Report Generation: Produces valid %PDF bytes with complete repository telemetry")
    void testPdfGenerationWithCompleteData() {
        RepositoryEntity repo = RepositoryEntity.builder()
                .id(UUID.randomUUID())
                .repositoryName("riskvision-test-repo")
                .repositoryUrl("https://github.com/test/repo")
                .owner("testowner")
                .organization("testorg")
                .language("Java")
                .openIssues(5)
                .contributors(3)
                .build();

        RepositoryPredictionEntity pred = RepositoryPredictionEntity.builder()
                .id(UUID.randomUUID())
                .repositoryId(repo.getId())
                .failureProbability(0.65)
                .riskScore(65)
                .riskLevel("HIGH")
                .confidence(0.85)
                .healthScore(70.0)
                .modelVersion("xgboost-v2.1")
                .featureImportanceJson("[{\"feature\":\"open_issues\",\"impact\":0.25,\"direction\":\"increases_risk\"}]")
                .recommendationsJson("{\"recommendations\":[{\"suggested_priority\":\"P1\",\"title\":\"Fix Issues\",\"risk_detected\":\"High open issue count\",\"recommended_action\":\"Triage issues\",\"why_it_matters\":\"Prevents backlog\",\"expected_impact\":\"High\",\"implementation_effort\":\"Medium\",\"estimated_risk_reduction\":\"15%\"}]}")
                .createdAt(LocalDateTime.now())
                .build();

        byte[] pdfBytes = pdfReportService.generatePredictionPdf(pred, repo);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 100);
        // Verify PDF Magic Bytes %PDF
        assertEquals('%', (char) pdfBytes[0]);
        assertEquals('P', (char) pdfBytes[1]);
        assertEquals('D', (char) pdfBytes[2]);
        assertEquals('F', (char) pdfBytes[3]);
    }

    @Test
    @DisplayName("PDF Report Generation: Gracefully renders report for minimal repository fields")
    void testPdfGenerationWithNullFields() {
        RepositoryEntity repo = RepositoryEntity.builder()
                .id(UUID.randomUUID())
                .repositoryName("null-fields-repo")
                .repositoryUrl("https://github.com/null/repo")
                .build();

        RepositoryPredictionEntity pred = RepositoryPredictionEntity.builder()
                .id(UUID.randomUUID())
                .repositoryId(repo.getId())
                .build();

        byte[] pdfBytes = pdfReportService.generatePredictionPdf(pred, repo);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 100);
    }

    @Test
    @DisplayName("Excel Report Generation: Produces valid XLSX spreadsheet bytes")
    void testExcelGenerationWithCompleteData() {
        RepositoryEntity repo = RepositoryEntity.builder()
                .id(UUID.randomUUID())
                .repositoryName("riskvision-test-repo")
                .repositoryUrl("https://github.com/test/repo")
                .owner("testowner")
                .language("Java")
                .build();

        RepositoryPredictionEntity pred = RepositoryPredictionEntity.builder()
                .id(UUID.randomUUID())
                .repositoryId(repo.getId())
                .failureProbability(0.65)
                .riskScore(65)
                .riskLevel("HIGH")
                .confidence(0.85)
                .healthScore(70.0)
                .modelVersion("xgboost-v2.1")
                .featureImportanceJson("[{\"feature\":\"open_issues\",\"impact\":0.25,\"direction\":\"increases_risk\"}]")
                .createdAt(LocalDateTime.now())
                .build();

        byte[] excelBytes = excelReportService.generatePredictionExcel(pred, repo);
        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 100);
        // Verify PK zip header signature for .xlsx
        assertEquals('P', (char) excelBytes[0]);
        assertEquals('K', (char) excelBytes[1]);
    }

    @Test
    @DisplayName("Report Generation Service: Generates valid Executive JSON and Projects CSV")
    void testReportGenerationService_JsonAndCsv() {
        when(userRepository.count()).thenReturn(5L);
        when(projectRepository.count()).thenReturn(10L);
        when(repositoryEntityRepository.count()).thenReturn(8L);
        when(auditLogRepository.count()).thenReturn(100L);

        byte[] jsonBytes = reportGenerationService.generateExecutiveSummaryJson();
        assertNotNull(jsonBytes);
        String jsonStr = new String(jsonBytes);
        assertTrue(jsonStr.contains("RiskVision AI Executive Platform Report"));
        assertTrue(jsonStr.contains("\"total_repositories\" : 8"));

        byte[] csvBytes = reportGenerationService.generateProjectsCsv();
        assertNotNull(csvBytes);
        String csvStr = new String(csvBytes);
        assertTrue(csvStr.startsWith("ID,ExternalID,Name,Status"));
    }

    @Test
    @DisplayName("Report Generation Service: Generates multi-repository batch ZIP package")
    void testReportGenerationService_BatchZipPackage() {
        RepositoryEntity repo = RepositoryEntity.builder()
                .id(UUID.randomUUID())
                .repositoryName("batch-repo-1")
                .repositoryUrl("https://github.com/batch/repo-1")
                .build();

        RepositoryPredictionEntity pred = RepositoryPredictionEntity.builder()
                .id(UUID.randomUUID())
                .repositoryId(repo.getId())
                .riskLevel("MEDIUM")
                .failureProbability(0.4)
                .createdAt(LocalDateTime.now())
                .build();

        when(repositoryEntityRepository.findAll()).thenReturn(List.of(repo));
        when(predictionRepository.findTopByRepositoryIdOrderByCreatedAtDesc(eq(repo.getId())))
                .thenReturn(Optional.of(pred));

        byte[] zipBytes = reportGenerationService.generateBatchZipPackageForRepos(null);
        assertNotNull(zipBytes);
        assertTrue(zipBytes.length > 200);
        // Verify ZIP header
        assertEquals('P', (char) zipBytes[0]);
        assertEquals('K', (char) zipBytes[1]);
    }
}
