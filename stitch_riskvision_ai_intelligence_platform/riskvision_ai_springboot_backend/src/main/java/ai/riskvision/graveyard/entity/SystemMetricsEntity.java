package ai.riskvision.graveyard.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "system_metrics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemMetricsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "cpu_usage")
    private Double cpuUsage;

    @Column(name = "memory_usage")
    private Double memoryUsage;

    @Column(name = "disk_usage")
    private Double diskUsage;

    @Column(name = "running_threads")
    private Integer runningThreads;

    @Column(name = "active_users")
    private Integer activeUsers;

    @Column(name = "db_connections")
    private Integer dbConnections;

    @Column(name = "api_response_time_ms")
    private Long apiResponseTimeMs;

    @Column(name = "model_inference_time_ms")
    private Long modelInferenceTimeMs;

    @Column(name = "server_uptime_seconds")
    private Long serverUptimeSeconds;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;
}
