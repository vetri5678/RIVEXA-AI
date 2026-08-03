package ai.riskvision.graveyard.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserResponse {
    private String id;
    private String email;
    private String username;
    
    @JsonProperty("full_name")
    private String fullName;
    
    private String role;
    
    @JsonProperty("is_active")
    private boolean isActive;

    @JsonProperty("avatar_url")
    private String avatarUrl;

    private String provider;

    @JsonProperty("login_count")
    private Integer loginCount;

    @JsonProperty("last_login")
    private String lastLogin;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("connected_accounts")
    private java.util.List<String> connectedAccounts;
}
