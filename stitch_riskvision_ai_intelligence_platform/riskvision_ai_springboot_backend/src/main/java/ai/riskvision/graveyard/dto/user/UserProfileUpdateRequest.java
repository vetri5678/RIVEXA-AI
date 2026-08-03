package ai.riskvision.graveyard.dto.user;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserProfileUpdateRequest {
    @Size(max = 255)
    private String fullName;

    @Size(max = 100)
    private String timezone;

    @Size(max = 20)
    private String language;

    private String preferences;
    private String notificationSettings;
    private Boolean mfaEnabled;
}
