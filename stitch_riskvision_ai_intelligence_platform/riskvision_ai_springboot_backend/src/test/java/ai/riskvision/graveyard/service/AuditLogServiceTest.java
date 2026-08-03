package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.entity.AuditLogEntity;
import ai.riskvision.graveyard.repository.AuditLogRepository;
import ai.riskvision.graveyard.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    private AuditLogEntity sampleEntity;

    @BeforeEach
    void setUp() {
        sampleEntity = AuditLogEntity.builder()
                .id(UUID.randomUUID())
                .eventType("USER_LOGIN")
                .eventTypeCompat("USER_LOGIN")
                .module("AUTH")
                .severity("MEDIUM")
                .status("success")
                .details("User logged in successfully")
                .username("admin@riskvision.ai")
                .ipAddress("127.0.0.1")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void testRecordEvent() {
        when(auditLogRepository.save(any(AuditLogEntity.class))).thenReturn(sampleEntity);

        AuditLogEntity result = auditLogService.recordEvent(
                "USER_LOGIN", "AUTH", "MEDIUM", "User logged in", "admin@riskvision.ai", "127.0.0.1"
        );

        assertNotNull(result);
        assertEquals("USER_LOGIN", result.getEventType());
        verify(auditLogRepository, times(1)).save(any(AuditLogEntity.class));
    }

    @Test
    void testGetAuditLogs() {
        when(auditLogRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleEntity)));

        Map<String, Object> result = auditLogService.getAuditLogs(0, 10);

        assertNotNull(result);
        assertTrue(result.containsKey("items"));
        assertEquals(1L, result.get("total"));
    }

    @Test
    void testGetRecentLive() {
        when(auditLogRepository.findTop20ByOrderByCreatedAtDesc())
                .thenReturn(List.of(sampleEntity));

        Map<String, Object> result = auditLogService.getRecentLive(10);

        assertNotNull(result);
        assertTrue(result.containsKey("items"));
        assertEquals(1, result.get("total"));
    }
}
