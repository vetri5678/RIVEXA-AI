package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.dto.auth.*;
import ai.riskvision.graveyard.service.AuthService;
import ai.riskvision.graveyard.service.EmailService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailService emailService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody UserRegisterRequest request) {
        try {
            UserResponse response = authService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("detail", ex.getMessage()));
        }
    }

    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam("token") String token) {
        try {
            authService.verifyEmail(token);
            return ResponseEntity.ok(Map.of("message", "Email verified successfully. You may now sign in."));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("detail", ex.getMessage()));
        }
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@RequestBody Map<String, String> body) {
        String email = body != null ? body.get("email") : null;
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("detail", "Email address is required."));
        }
        try {
            authService.resendVerification(email);
            return ResponseEntity.ok(Map.of("message", "Verification email dispatched. Please check your inbox."));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("detail", ex.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody UserLoginRequest request) {
        try {
            TokenResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", ex.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody(required = false) Map<String, String> body) {
        String refreshToken = body != null ? body.get("refresh_token") : null;
        try {
            authService.logout(refreshToken);
            return ResponseEntity.ok(Map.of("message", "Logged out successfully. Token revoked."));
        } catch (Exception ex) {
            return ResponseEntity.ok(Map.of("message", "Logout processing completed."));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) Map<String, String> body) {

        String refreshToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            refreshToken = authHeader.substring(7);
        } else if (body != null) {
            refreshToken = body.get("refresh_token");
        }

        if (refreshToken == null || refreshToken.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("detail", "Missing refresh token."));
        }

        try {
            TokenResponse response = authService.refreshToken(refreshToken);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", ex.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Not authenticated"));
        }
        try {
            UserResponse response = authService.getMe(principal.getName());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", ex.getMessage()));
        }
    }

    @PostMapping("/password-reset")
    public ResponseEntity<?> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        try {
            authService.requestPasswordReset(request.getEmail());
            return ResponseEntity.ok(Map.of("message",
                    "If the email address is registered, a 6-digit OTP code has been dispatched to your inbox."));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("detail", "Failed to initiate password reset: " + ex.getMessage()));
        }
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<?> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        String otpCode = request.getOtpOrToken();
        if (otpCode == null || otpCode.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("detail", "OTP verification code is required."));
        }
        try {
            authService.confirmPasswordReset(otpCode, request.getNewPassword());
            return ResponseEntity.ok(Map.of("message",
                    "Password has been successfully updated. Please sign in with your new password."));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("detail", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("detail", "Failed to reset password: " + ex.getMessage()));
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest request, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Not authenticated"));
        }
        try {
            authService.changePassword(principal.getName(), request.getOldPassword(), request.getNewPassword());
            return ResponseEntity
                    .ok(Map.of("message", "Password changed successfully. All other sessions have been logged out."));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("detail", ex.getMessage()));
        }
    }

    @PostMapping("/test-email")
    public ResponseEntity<?> testEmail(@RequestParam("to") String toEmail) {
        try {
            emailService.sendNotificationEmail(toEmail, "RiskVision AI SMTP Test",
                    "Your Gmail SMTP server configuration is working perfectly!");
            return ResponseEntity.ok(Map.of("message", "Test email successfully sent to " + toEmail));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("detail", "SMTP test failed: " + ex.getMessage()));
        }
    }

    @PostMapping("/oauth2/complete-email")
    public ResponseEntity<?> completeOAuthEmail(@Valid @RequestBody CompleteOAuthEmailRequest request) {
        try {
            TokenResponse tokenResponse = authService.completeOAuthRegistration(
                    request.getEmail(),
                    request.getProvider(),
                    request.getProviderUserId(),
                    request.getUsername(),
                    request.getFullName(),
                    request.getAvatarUrl());
            return ResponseEntity.ok(tokenResponse);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("detail", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("detail", "Failed to complete authentication: " + ex.getMessage()));
        }
    }

    @Data
    public static class ChangePasswordRequest {
        @jakarta.validation.constraints.NotBlank
        private String oldPassword;

        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(min = 8, message = "New password must be at least 8 characters")
        private String newPassword;
    }

    @Data
    public static class CompleteOAuthEmailRequest {
        @jakarta.validation.constraints.Email
        @jakarta.validation.constraints.NotBlank
        private String email;

        @jakarta.validation.constraints.NotBlank
        private String provider;

        @jakarta.validation.constraints.NotBlank
        private String providerUserId;

        private String username;
        private String fullName;
        private String avatarUrl;
    }
}
