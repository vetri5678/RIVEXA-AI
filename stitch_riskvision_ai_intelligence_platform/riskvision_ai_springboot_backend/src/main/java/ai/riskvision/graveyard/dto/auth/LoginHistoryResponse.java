package ai.riskvision.graveyard.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginHistoryResponse {
    
    private String id;
    
    @JsonProperty("user_id")
    private String userId;
    
    private String username;
    
    @JsonProperty("full_name")
    private String fullName;
    
    private String email;
    
    @JsonProperty("ip_address")
    private String ipAddress;
    
    @JsonProperty("user_agent")
    private String userAgent;
    
    private String provider;
    
    private String browser;
    
    @JsonProperty("operating_system")
    private String operatingSystem;
    
    @JsonProperty("session_id")
    private String sessionId;
    
    private boolean success;
    
    @JsonProperty("failure_reason")
    private String failureReason;
    
    @JsonProperty("created_at")
    private String createdAt;
}
