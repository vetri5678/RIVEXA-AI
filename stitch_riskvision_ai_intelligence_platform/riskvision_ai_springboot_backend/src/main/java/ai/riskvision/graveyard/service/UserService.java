package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.dto.common.PageResponse;
import ai.riskvision.graveyard.dto.user.UserDto;
import ai.riskvision.graveyard.dto.user.UserProfileUpdateRequest;
import ai.riskvision.graveyard.entity.UserEntity;
import ai.riskvision.graveyard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<UserDto> searchUsers(String query, String role, Boolean isActive, int page, int size, String sortBy, String direction) {
        Sort sort = direction.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<UserEntity> userPage = userRepository.searchUsers(query, role, isActive, pageable);
        return PageResponse.fromPage(userPage.map(this::mapToDto));
    }

    @Transactional(readOnly = true)
    public UserDto getUserById(UUID id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + id));
        return mapToDto(user);
    }

    @Transactional(readOnly = true)
    public UserDto getUserByEmail(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));
        return mapToDto(user);
    }

    @Transactional
    public UserDto updateProfile(String userEmail, UserProfileUpdateRequest request) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getTimezone() != null) user.setTimezone(request.getTimezone());
        if (request.getLanguage() != null) user.setLanguage(request.getLanguage());
        if (request.getPreferences() != null) user.setPreferences(request.getPreferences());
        if (request.getNotificationSettings() != null) user.setNotificationSettings(request.getNotificationSettings());
        if (request.getMfaEnabled() != null) user.setMfaEnabled(request.getMfaEnabled());

        UserEntity updated = userRepository.save(user);
        log.info("User profile updated for user: {}", userEmail);
        return mapToDto(updated);
    }

    @Transactional
    public UserDto updateUserRole(UUID userId, String newRole) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setRole(newRole.toUpperCase());
        UserEntity updated = userRepository.save(user);
        log.info("Updated role for user {} to {}", userId, newRole);
        return mapToDto(updated);
    }

    @Transactional
    public UserDto setUserStatus(UUID userId, boolean isActive) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setIsActive(isActive);
        UserEntity updated = userRepository.save(user);
        log.info("Set active status for user {} to {}", userId, isActive);
        return mapToDto(updated);
    }

    @Transactional
    public void deleteUser(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
        userRepository.deleteById(userId);
        log.info("Deleted user {}", userId);
    }

    @Transactional(readOnly = true)
    public byte[] exportUsersCsv() {
        List<UserEntity> users = userRepository.findAll();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(out)) {
            writer.println("ID,Username,Email,FullName,Role,IsActive,IsVerified,Provider,CreatedAt");
            for (UserEntity u : users) {
                writer.printf("%s,\"%s\",\"%s\",\"%s\",\"%s\",%b,%b,\"%s\",\"%s\"%n",
                        u.getId(), u.getUsername(), u.getEmail(),
                        u.getFullName() != null ? u.getFullName() : "",
                        u.getRole(), u.getIsActive(), u.getIsVerified(),
                        u.getProvider(), u.getCreatedAt());
            }
            writer.flush();
        }
        return out.toByteArray();
    }

    public UserDto mapToDto(UserEntity entity) {
        return UserDto.builder()
                .id(entity.getId())
                .email(entity.getEmail())
                .username(entity.getUsername())
                .fullName(entity.getFullName())
                .role(entity.getRole())
                .isVerified(entity.getIsVerified())
                .isActive(entity.getIsActive())
                .provider(entity.getProvider())
                .avatarUrl(entity.getAvatarUrl())
                .timezone(entity.getTimezone())
                .language(entity.getLanguage())
                .mfaEnabled(entity.getMfaEnabled())
                .preferences(entity.getPreferences())
                .notificationSettings(entity.getNotificationSettings())
                .loginCount(entity.getLoginCount())
                .lastLogin(entity.getLastLogin())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
