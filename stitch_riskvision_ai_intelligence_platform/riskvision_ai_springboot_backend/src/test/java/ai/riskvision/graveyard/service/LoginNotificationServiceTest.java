package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.entity.LoginHistoryEntity;
import ai.riskvision.graveyard.entity.UserEntity;
import ai.riskvision.graveyard.repository.LoginHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LoginNotificationServiceTest {

    @Mock
    private LoginHistoryRepository loginHistoryRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private LoginNotificationService loginNotificationService;

    private UUID historyId;
    private UserEntity user;
    private LoginHistoryEntity successfulHistory;

    @BeforeEach
    void setUp() {
        historyId = UUID.randomUUID();
        user = UserEntity.builder()
                .id(UUID.randomUUID())
                .email("admin@riskvision.ai")
                .username("admin")
                .fullName("System Administrator")
                .role("ADMIN")
                .build();

        successfulHistory = LoginHistoryEntity.builder()
                .id(historyId)
                .user(user)
                .email("admin@riskvision.ai")
                .ipAddress("127.0.0.1")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .provider("email")
                .browser("Chrome 125")
                .operatingSystem("Windows 10")
                .success(true)
                .emailNotified(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Verified successful authentication event dispatches email and sets emailNotified=true")
    void testSendAdminLoginNotification_Success() {
        given(loginHistoryRepository.findById(historyId)).willReturn(Optional.of(successfulHistory));

        loginNotificationService.sendAdminLoginNotification(historyId);

        ArgumentCaptor<LoginHistoryEntity> captor = ArgumentCaptor.forClass(LoginHistoryEntity.class);
        verify(loginHistoryRepository).save(captor.capture());
        assertTrue(captor.getValue().getEmailNotified());

        verify(emailService, times(1)).sendLoginNotificationEmail(
                eq(user.getId().toString()),
                eq("admin@riskvision.ai"),
                eq("System Administrator"),
                any(LocalDateTime.class),
                eq("Credentials"),
                eq("Chrome 125"),
                eq("Windows 10"),
                eq("127.0.0.1")
        );
    }

    @Test
    @DisplayName("Idempotency guard prevents duplicate emails for the same login event")
    void testSendAdminLoginNotification_DuplicateSkipped() {
        LoginHistoryEntity alreadyNotified = LoginHistoryEntity.builder()
                .id(historyId)
                .user(user)
                .email("admin@riskvision.ai")
                .success(true)
                .emailNotified(true)
                .build();

        given(loginHistoryRepository.findById(historyId)).willReturn(Optional.of(alreadyNotified));

        loginNotificationService.sendAdminLoginNotification(historyId);

        verify(emailService, never()).sendLoginNotificationEmail(any(), any(), any(), any(), any(), any(), any(), any());
        verify(loginHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Security guard rejects notification if authentication success is false")
    void testSendAdminLoginNotification_FailedLoginRejected() {
        LoginHistoryEntity failedHistory = LoginHistoryEntity.builder()
                .id(historyId)
                .user(user)
                .email("admin@riskvision.ai")
                .success(false)
                .emailNotified(false)
                .build();

        given(loginHistoryRepository.findById(historyId)).willReturn(Optional.of(failedHistory));

        loginNotificationService.sendAdminLoginNotification(historyId);

        verify(emailService, never()).sendLoginNotificationEmail(any(), any(), any(), any(), any(), any(), any(), any());
        verify(loginHistoryRepository, never()).save(any());
    }
}
