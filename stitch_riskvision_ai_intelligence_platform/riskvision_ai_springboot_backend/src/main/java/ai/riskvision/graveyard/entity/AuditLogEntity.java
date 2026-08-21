package ai.riskvision.graveyard.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private UserEntity user;

    @Column(name = "action", nullable = false, length = 100)
    private String eventType;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventTypeCompat;

    @Column(name = "module", length = 50)
    @Builder.Default
    private String module = "SYSTEM";

    @Column(name = "severity", length = 20)
    @Builder.Default
    private String severity = "LOW";

    @Builder.Default
    @Column(name = "status", length = 20)
    private String status = "success";

    @Column(name = "description", columnDefinition = "TEXT")
    private String details;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "resource_type", length = 50)
    private String resourceType;

    @Column(name = "resource_id", length = 100)
    private String resourceId;

    @Column(name = "endpoint", length = 255)
    private String endpoint;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @CreationTimestamp
    @Column(name = "timestamp", updatable = false)
    private LocalDateTime createdAt;
}
