package ai.riskvision.graveyard.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "prediction_metrics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PredictionMetricsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "analyzed_today")
    private Integer analyzedToday;

    @Column(name = "alive_count")
    private Integer aliveCount;

    @Column(name = "at_risk_count")
    private Integer atRiskCount;

    @Column(name = "dead_count")
    private Integer deadCount;

    @Column(name = "pending_count")
    private Integer pendingCount;

    @Column(name = "avg_confidence_today")
    private Double avgConfidenceToday;

    @Column(name = "high_confidence_count")
    private Integer highConfidenceCount;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;
}
