package ai.riskvision.graveyard.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "model_performance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelPerformanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "model_name")
    private String modelName;

    @Column(name = "algorithm")
    private String algorithm;

    @Column(name = "accuracy")
    private Double accuracy;

    @Column(name = "precision_val")
    private Double precisionVal;

    @Column(name = "recall")
    private Double recall;

    @Column(name = "f1_score")
    private Double f1Score;

    @Column(name = "roc_auc")
    private Double rocAuc;

    @Column(name = "cv_score")
    private Double cvScore;

    @Column(name = "dataset_version")
    private String datasetVersion;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;
}
