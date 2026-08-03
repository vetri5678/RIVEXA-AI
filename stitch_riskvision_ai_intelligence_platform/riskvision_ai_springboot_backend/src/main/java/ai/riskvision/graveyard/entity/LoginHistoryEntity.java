package ai.riskvision.graveyard.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "login_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private UserEntity user;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    /** Authentication provider: "google", "github", "email". */
    @Column(name = "provider", length = 50)
    private String provider;

    /** Parsed browser name from User-Agent (e.g. "Chrome 125", "Firefox 127"). */
    @Column(name = "browser", length = 100)
    private String browser;

    /** Parsed operating system from User-Agent (e.g. "Windows 10", "macOS"). */
    @Column(name = "operating_system", length = 100)
    private String operatingSystem;

    /** JWT session correlation ID (jti or access-token hash prefix). */
    @Column(name = "session_id", length = 100)
    private String sessionId;

    /** Country resolved from IP (geo-lookup placeholder; can be enriched later). */
    @Column(name = "country", length = 100)
    private String country;

    /** City resolved from IP (geo-lookup placeholder; can be enriched later). */
    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "success", nullable = false)
    private Boolean success;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
