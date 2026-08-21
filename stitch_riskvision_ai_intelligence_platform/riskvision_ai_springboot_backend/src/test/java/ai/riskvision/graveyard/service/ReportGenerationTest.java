package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.client.OpenRouterClient;
import ai.riskvision.graveyard.entity.RepositoryEntity;
import ai.riskvision.graveyard.entity.RepositoryPredictionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class ReportGenerationTest {

    @Mock
    private OpenRouterClient openRouterClient;

    private ObjectMapper objectMapper;
    private PdfReportService pdfReportService;
    private ExcelReportService excelReportService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        pdfReportService = new PdfReportService(openRouterClient, objectMapper);
        excelReportService = new ExcelReportService(objectMapper);
    }

    @Test
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
        assertTrue(pdfBytes.length > 0);
    }

    @Test
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
        assertTrue(pdfBytes.length > 0);
    }

    @Test
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
        assertTrue(excelBytes.length > 0);
    }

    @Test
    void testExcelGenerationWithNullFields() {
        RepositoryEntity repo = RepositoryEntity.builder()
                .id(UUID.randomUUID())
                .repositoryName("null-fields-repo")
                .repositoryUrl("https://github.com/null/repo")
                .build();

        RepositoryPredictionEntity pred = RepositoryPredictionEntity.builder()
                .id(UUID.randomUUID())
                .repositoryId(repo.getId())
                .build();

        byte[] excelBytes = excelReportService.generatePredictionExcel(pred, repo);
        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 0);
    }
}
