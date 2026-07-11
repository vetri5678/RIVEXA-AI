package ai.riskvision.graveyard.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "repository_activities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepositoryActivityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "actor", length = 200)
    private String actor;

    @Column(name = "resource_type", length = 100)
    private String resourceType;

    @Column(name = "metadata", length = 1000)
    private String metadata;

    @Column(name = "severity", length = 50)
    @Builder.Default
    private String severity = "INFO";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
