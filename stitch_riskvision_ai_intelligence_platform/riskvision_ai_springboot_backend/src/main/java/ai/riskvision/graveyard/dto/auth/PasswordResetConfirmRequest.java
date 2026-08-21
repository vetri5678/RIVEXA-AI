package ai.riskvision.graveyard.dto.auth;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordResetConfirmRequest {

    @JsonProperty("token")
    @JsonAlias({"token", "otp", "otpCode", "otp_code", "otpOrToken"})
    private String token;

    @JsonProperty("otp")
    @JsonAlias({"token", "otp", "otpCode", "otp_code", "otpOrToken"})
    private String otp;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    @JsonProperty("new_password")
    @JsonAlias({"newPassword", "new_password", "newPass", "password"})
    private String newPassword;

    public String getOtpOrToken() {
        if (otp != null && !otp.trim().isEmpty()) {
            return otp.trim();
        }
        if (token != null && !token.trim().isEmpty()) {
            return token.trim();
        }
        return null;
    }
}

