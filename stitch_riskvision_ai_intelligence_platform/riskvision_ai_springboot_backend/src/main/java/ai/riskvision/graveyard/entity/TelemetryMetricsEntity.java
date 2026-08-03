package ai.riskvision.graveyard.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "telemetry_metrics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelemetryMetricsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "cpu_usage")
    private Double cpuUsage;

    @Column(name = "memory_usage")
    private Double memoryUsage;

    @Column(name = "heap_usage")
    private Double heapUsage;

    @Column(name = "disk_usage")
    private Double diskUsage;

    @Column(name = "network_usage")
    private Double networkUsage;

    @Column(name = "thread_count")
    private Integer threadCount;

    @Column(name = "active_sessions")
    private Integer activeSessions;

    @Column(name = "api_latency")
    private Long apiLatency;

    @Column(name = "prediction_latency")
    private Long predictionLatency;

    @Column(name = "commits_count")
    private Integer commitsCount;

    @Column(name = "pull_requests_count")
    private Integer pullRequestsCount;

    @Column(name = "failed_builds_count")
    private Integer failedBuildsCount;

    @Column(name = "successful_builds_count")
    private Integer successfulBuildsCount;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;
}
