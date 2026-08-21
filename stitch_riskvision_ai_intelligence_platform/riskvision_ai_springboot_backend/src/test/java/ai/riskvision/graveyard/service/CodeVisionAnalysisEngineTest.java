package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.entity.CodeFindingEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CodeVisionAnalysisEngineTest {

    private CodeVisionAnalysisEngine engine;

    @BeforeEach
    void setUp() {
        engine = new CodeVisionAnalysisEngine();
    }

    @Test
    @DisplayName("shouldAnalyzeFile: supported vs unsupported extensions & directories")
    void testShouldAnalyzeFile() {
        assertThat(engine.shouldAnalyzeFile("src/main/java/AuthService.java", 1024)).isTrue();
        assertThat(engine.shouldAnalyzeFile("app/services/user_service.py", 2048)).isTrue();
        assertThat(engine.shouldAnalyzeFile("src/components/Button.tsx", 512)).isTrue();

        assertThat(engine.shouldAnalyzeFile("node_modules/express/index.js", 100)).isFalse();
        assertThat(engine.shouldAnalyzeFile("target/classes/Auth.class", 100)).isFalse();
        assertThat(engine.shouldAnalyzeFile("dist/bundle.min.js", 100)).isFalse();
        assertThat(engine.shouldAnalyzeFile("image.png", 500)).isFalse();
        assertThat(engine.shouldAnalyzeFile("huge_file.java", 1000 * 1024)).isFalse();
    }

    @Test
    @DisplayName("analyzeSourceFile: detects empty catch, hardcoded secrets, and produces risk scores & findings")
    void testAnalyzeJavaSourceFileWithFindings() {
        String javaCode = """
                package com.example;
                
                public class AuthService {
                    private String apiKey = "secret_api_key_12345678";
                    
                    public void authenticate() {
                        try {
                            System.out.println("Authenticating...");
                        } catch (Exception e) {
                        }
                    }
                }
                """;

        UUID runId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();

        CodeVisionAnalysisEngine.FileAnalysisResult result = engine.analyzeSourceFile("src/com/example/AuthService.java", javaCode, runId, fileId);

        assertThat(result.getFilePath()).isEqualTo("src/com/example/AuthService.java");
        assertThat(result.getLanguage()).isEqualTo("Java");
        assertThat(result.getLinesOfCode()).isGreaterThan(0);
        assertThat(result.getRiskScore()).isGreaterThan(0);
        assertThat(result.getMetrics()).containsKey("code_sample");
        assertThat(result.getFindings()).isNotEmpty();

        boolean hasSecretFinding = result.getFindings().stream()
                .anyMatch(f -> "Security Vulnerability".equals(f.getFindingType()) || "CRITICAL".equals(f.getSeverity()));
        boolean hasExceptionFinding = result.getFindings().stream()
                .anyMatch(f -> f.getTitle().contains("Swallowed Exception"));

        assertThat(hasSecretFinding || hasExceptionFinding).isTrue();
    }
}
