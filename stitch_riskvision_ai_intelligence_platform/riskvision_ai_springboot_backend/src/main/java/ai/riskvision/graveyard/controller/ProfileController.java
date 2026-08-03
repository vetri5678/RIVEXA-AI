package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.dto.auth.UserResponse;
import ai.riskvision.graveyard.service.AuthService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/profile", "/api/profile"})
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProfileController {

    private final AuthService authService;

    @GetMapping
    public ResponseEntity<?> getProfile(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        return ResponseEntity.ok(authService.getMe(principal.getName()));
    }

    @PostMapping("/update")
    public ResponseEntity<?> updateProfile(@RequestBody UpdateProfileRequest request, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        UserResponse user = authService.updateProfile(principal.getName(), request.getFullName(), request.getAvatarUrl());
        return ResponseEntity.ok(user);
    }

    @PostMapping("/connect")
    public ResponseEntity<?> connectAccount(@RequestBody ConnectAccountRequest request, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        try {
            UserResponse user = authService.linkOAuthAccount(
                    principal.getName(),
                    request.getProvider(),
                    request.getProviderUserId(),
                    request.getUsername(),
                    request.getFullName(),
                    request.getAvatarUrl()
            );
            return ResponseEntity.ok(user);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/disconnect")
    public ResponseEntity<?> disconnectAccount(@RequestBody Map<String, String> request, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        String provider = request.get("provider");
        if (provider == null || provider.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Provider is required."));
        }
        try {
            UserResponse user = authService.unlinkOAuthAccount(principal.getName(), provider);
            return ResponseEntity.ok(user);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Data
    public static class UpdateProfileRequest {
        private String fullName;
        private String avatarUrl;
    }

    @Data
    public static class ConnectAccountRequest {
        private String provider;
        private String providerUserId;
        private String username;
        private String fullName;
        private String avatarUrl;
    }
}
