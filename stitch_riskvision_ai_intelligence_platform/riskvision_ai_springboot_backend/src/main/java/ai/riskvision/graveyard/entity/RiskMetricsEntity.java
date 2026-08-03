package ai.riskvision.graveyard.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "risk_metrics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskMetricsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "graveyard_index")
    private Double graveyardIndex;

    @Column(name = "health_score")
    private Double healthScore;

    @Column(name = "avg_failure_probability")
    private Double avgFailureProbability;

    @Column(name = "healthy_count")
    private Integer healthyCount;

    @Column(name = "at_risk_count")
    private Integer atRiskCount;

    @Column(name = "critical_count")
    private Integer criticalCount;

    @Column(name = "total_analyzed")
    private Integer totalAnalyzed;

    @Column(name = "trend")
    private Double trend;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;
}
