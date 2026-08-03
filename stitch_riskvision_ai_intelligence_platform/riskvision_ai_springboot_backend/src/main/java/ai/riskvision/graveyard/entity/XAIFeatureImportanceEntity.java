package ai.riskvision.graveyard.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "xai_feature_importance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class XAIFeatureImportanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "feature_name")
    private String featureName;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "avg_impact")
    private Double avgImpact;

    @Column(name = "contribution_pct")
    private Double contributionPct;

    @Column(name = "occurrence_count")
    private Integer occurrenceCount;

    @Column(name = "direction")
    private String direction;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;
}
