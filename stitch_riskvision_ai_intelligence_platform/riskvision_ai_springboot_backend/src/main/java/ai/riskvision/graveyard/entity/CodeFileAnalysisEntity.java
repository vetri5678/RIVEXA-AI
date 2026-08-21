package ai.riskvision.graveyard.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "code_file_analyses", indexes = {
    @Index(name = "idx_code_file_analyses_run", columnList = "analysis_run_id"),
    @Index(name = "idx_code_file_analyses_repo_path", columnList = "repository_id, file_path")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeFileAnalysisEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "analysis_run_id", nullable = false)
    private UUID analysisRunId;

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Column(name = "file_path", nullable = false, columnDefinition = "TEXT")
    private String filePath;

    @Column(name = "file_hash", length = 64)
    private String fileHash;

    @Column(name = "language", length = 32)
    private String language;

    @Column(name = "lines_of_code")
    @Builder.Default
    private Integer linesOfCode = 0;

    @Column(name = "risk_score")
    @Builder.Default
    private Integer riskScore = 0; // 0-100

    @Column(name = "severity", nullable = false, length = 16)
    @Builder.Default
    private String severity = "LOW"; // LOW, MEDIUM, HIGH, CRITICAL

    @Column(name = "confidence")
    @Builder.Default
    private Integer confidence = 0; // 0-100

    @Column(name = "analysis_type", nullable = false, length = 16)
    @Builder.Default
    private String analysisType = "HYBRID"; // STATIC, HYBRID, ML

    @Column(name = "metrics_json", columnDefinition = "TEXT")
    private String metricsJson;

    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private String status = "ANALYZED"; // ANALYZED, FAILED, SKIPPED

    @CreationTimestamp
    @Column(name = "analyzed_at", nullable = false, updatable = false)
    private Instant analyzedAt;
}
