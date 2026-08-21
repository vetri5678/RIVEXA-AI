package ai.riskvision.graveyard.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "code_analysis_runs", indexes = {
    @Index(name = "idx_code_analysis_runs_user_repo", columnList = "user_id, repository_id"),
    @Index(name = "idx_code_analysis_runs_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeAnalysisRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "QUEUED"; // QUEUED, RUNNING, COMPLETED, PARTIAL_SUCCESS, FAILED, CANCELLED

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "files_discovered")
    @Builder.Default
    private Integer filesDiscovered = 0;

    @Column(name = "files_analyzed")
    @Builder.Default
    private Integer filesAnalyzed = 0;

    @Column(name = "files_with_findings")
    @Builder.Default
    private Integer filesWithFindings = 0;

    @Column(name = "currently_analyzing_file", columnDefinition = "TEXT")
    private String currentlyAnalyzingFile;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
