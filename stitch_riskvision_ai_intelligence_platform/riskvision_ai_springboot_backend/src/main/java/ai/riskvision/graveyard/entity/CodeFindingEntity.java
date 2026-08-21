package ai.riskvision.graveyard.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "code_findings", indexes = {
    @Index(name = "idx_code_findings_file", columnList = "file_analysis_id"),
    @Index(name = "idx_code_findings_run", columnList = "analysis_run_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeFindingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "file_analysis_id", nullable = false)
    private UUID fileAnalysisId;

    @Column(name = "analysis_run_id", nullable = false)
    private UUID analysisRunId;

    @Column(name = "finding_type", nullable = false, length = 64)
    private String findingType;

    @Column(name = "severity", nullable = false, length = 16)
    @Builder.Default
    private String severity = "MEDIUM"; // LOW, MEDIUM, HIGH, CRITICAL

    @Column(name = "confidence")
    @Builder.Default
    private Integer confidence = 0; // 0-100

    @Column(name = "symbol_name", columnDefinition = "TEXT")
    private String symbolName; // Function, method, class, or component name

    @Column(name = "start_line")
    private Integer startLine;

    @Column(name = "end_line")
    private Integer endLine;

    @Column(name = "title", nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "evidence", columnDefinition = "TEXT")
    private String evidence;

    @Column(name = "recommendation", columnDefinition = "TEXT")
    private String recommendation;

    @Column(name = "analysis_source", nullable = false, length = 16)
    @Builder.Default
    private String analysisSource = "STATIC"; // STATIC, HYBRID, ML

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
