package ai.riskvision.graveyard.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserLoginRequest {

    @NotBlank(message = "Email/Username is required")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}
