package ai.riskvision.graveyard.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private UUID id;
    private String email;
    private String username;
    private String fullName;
    private String role;
    private Boolean isVerified;
    private Boolean isActive;
    private String provider;
    private String avatarUrl;
    private String timezone;
    private String language;
    private Boolean mfaEnabled;
    private String preferences;
    private String notificationSettings;
    private Integer loginCount;
    private LocalDateTime lastLogin;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
