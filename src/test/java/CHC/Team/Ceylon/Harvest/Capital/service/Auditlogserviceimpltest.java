package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.dto.AuditLogResponse;
import CHC.Team.Ceylon.Harvest.Capital.entity.AuditLog;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.AuditActionType;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import CHC.Team.Ceylon.Harvest.Capital.exception.ResourceNotFoundException;
import CHC.Team.Ceylon.Harvest.Capital.repository.AuditLogRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.service.impl.AuditLogServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// AC-1: audit_log table captures every approval and rejection action
// AC-2: each log entry contains action type, milestone ID, farmer name, auditor ID, and timestamp
// AC-3: auditor can retrieve their full audit history
// AC-4: entries are returned sorted most-recent first
// AC-5: the service never exposes update or delete operations
@ExtendWith(MockitoExtension.class)
class AuditLogServiceImplTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    private AuditLogServiceImpl auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogServiceImpl(auditLogRepository, userRepository);
    }

    // ── AC-1 + AC-2: log() persists a complete entry for an APPROVED action ──

    @Test
    void log_shouldPersistApprovalEntryWithAllRequiredFields() {
        User auditor = buildAuditor(88L, "Ayesha Auditor");
        when(userRepository.findById(88L)).thenReturn(Optional.of(auditor));

        auditLogService.log(AuditActionType.APPROVED, 101L, "Nuwan Perera", 88L);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals(AuditActionType.APPROVED, saved.getActionType());
        assertEquals(101L, saved.getMilestoneId());
        assertEquals("Nuwan Perera", saved.getFarmerName());
        assertEquals(auditor, saved.getAuditor());
    }

    // ── AC-1 + AC-2: log() persists a complete entry for a REJECTED action ───

    @Test
    void log_shouldPersistRejectionEntryWithAllRequiredFields() {
        User auditor = buildAuditor(88L, "Ayesha Auditor");
        when(userRepository.findById(88L)).thenReturn(Optional.of(auditor));

        auditLogService.log(AuditActionType.REJECTED, 102L, "Amali Silva", 88L);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals(AuditActionType.REJECTED, saved.getActionType());
        assertEquals(102L, saved.getMilestoneId());
        assertEquals("Amali Silva", saved.getFarmerName());
        assertEquals(auditor, saved.getAuditor());
    }

    // ── AC-2: log() throws when auditor user does not exist ──────────────────

    @Test
    void log_shouldThrowResourceNotFoundWhenAuditorDoesNotExist() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> auditLogService.log(AuditActionType.APPROVED, 101L, "Nuwan Perera", 999L));

        assertTrue(ex.getMessage().contains("999"));
        verify(auditLogRepository, never()).save(any());
    }

    // ── AC-3: getHistoryForAuditor() returns all entries mapped to response DTOs ──

    @Test
    void getHistoryForAuditor_shouldReturnMappedResponseList() {
        User auditor = buildAuditor(88L, "Ayesha Auditor");

        AuditLog entry1 = buildAuditLog(7L, AuditActionType.APPROVED, 12L, "Kamal Perera", auditor,
                LocalDateTime.of(2026, 3, 25, 11, 0));
        AuditLog entry2 = buildAuditLog(6L, AuditActionType.REJECTED, 10L, "Nuwan Perera", auditor,
                LocalDateTime.of(2026, 3, 24, 9, 30));

        when(auditLogRepository.findAllByAuditorIdOrderByActionedAtDesc(88L))
                .thenReturn(List.of(entry1, entry2));

        List<AuditLogResponse> result = auditLogService.getHistoryForAuditor(88L);

        assertEquals(2, result.size());

        AuditLogResponse first = result.get(0);
        assertEquals(7L, first.auditLogId());
        assertEquals(AuditActionType.APPROVED, first.actionType());
        assertEquals(12L, first.milestoneId());
        assertEquals("Kamal Perera", first.farmerName());
        assertEquals(88L, first.auditorId());
        assertEquals(LocalDateTime.of(2026, 3, 25, 11, 0), first.actionedAt());
    }

    // ── AC-4: getHistoryForAuditor() delegates ordering to the repository ────

    @Test
    void getHistoryForAuditor_shouldReturnEntriesMostRecentFirst() {
        User auditor = buildAuditor(88L, "Ayesha Auditor");

        AuditLog newer = buildAuditLog(9L, AuditActionType.APPROVED, 15L, "Saman Fernando", auditor,
                LocalDateTime.of(2026, 3, 27, 14, 0));
        AuditLog older = buildAuditLog(5L, AuditActionType.REJECTED, 8L, "Amali Silva", auditor,
                LocalDateTime.of(2026, 3, 20, 10, 0));

        // Repository contract: already ordered desc — service must preserve that order
        when(auditLogRepository.findAllByAuditorIdOrderByActionedAtDesc(88L))
                .thenReturn(List.of(newer, older));

        List<AuditLogResponse> result = auditLogService.getHistoryForAuditor(88L);

        assertEquals(9L, result.get(0).auditLogId());
        assertEquals(5L, result.get(1).auditLogId());
        assertTrue(result.get(0).actionedAt().isAfter(result.get(1).actionedAt()));
    }

    // ── AC-3: getHistoryForAuditor() returns empty list when no logs exist ───

    @Test
    void getHistoryForAuditor_shouldReturnEmptyListWhenNoLogsExist() {
        when(auditLogRepository.findAllByAuditorIdOrderByActionedAtDesc(88L))
                .thenReturn(List.of());

        List<AuditLogResponse> result = auditLogService.getHistoryForAuditor(88L);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ── AC-5: service exposes no delete or update operations ─────────────────

    @Test
    void log_shouldOnlySaveAndNeverDeleteOrUpdate() {
        User auditor = buildAuditor(88L, "Ayesha Auditor");
        when(userRepository.findById(88L)).thenReturn(Optional.of(auditor));

        auditLogService.log(AuditActionType.APPROVED, 101L, "Nuwan Perera", 88L);

        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
        verify(auditLogRepository, never()).delete(any());
        verify(auditLogRepository, never()).deleteById(any());
        verify(auditLogRepository, never()).deleteAll();
    }

    // ── AC-2: response DTO contains all required fields including timestamp ───

    @Test
    void getHistoryForAuditor_shouldIncludeTimestampInResponse() {
        User auditor = buildAuditor(55L, "Roshan Perera");
        LocalDateTime expectedTimestamp = LocalDateTime.of(2026, 3, 26, 8, 45);

        AuditLog entry = buildAuditLog(3L, AuditActionType.REJECTED, 20L, "Ruvini Jayawardena",
                auditor, expectedTimestamp);

        when(auditLogRepository.findAllByAuditorIdOrderByActionedAtDesc(55L))
                .thenReturn(List.of(entry));

        List<AuditLogResponse> result = auditLogService.getHistoryForAuditor(55L);

        assertEquals(1, result.size());
        assertEquals(expectedTimestamp, result.get(0).actionedAt());
        assertEquals(55L, result.get(0).auditorId());
        assertEquals(AuditActionType.REJECTED, result.get(0).actionType());
        assertEquals(20L, result.get(0).milestoneId());
        assertEquals("Ruvini Jayawardena", result.get(0).farmerName());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User buildAuditor(Long id, String fullName) {
        User user = new User();
        user.setUserId(id);
        user.setFullName(fullName);
        user.setEmail("auditor" + id + "@chc.lk");
        user.setRole(Role.AUDITOR);
        user.setVerificationStatus(VerificationStatus.VERIFIED);
        user.setPasswordHash("hashed");
        return user;
    }

    private AuditLog buildAuditLog(Long id, AuditActionType actionType, Long milestoneId,
                                   String farmerName, User auditor, LocalDateTime actionedAt) {
        AuditLog log = new AuditLog(actionType, milestoneId, farmerName, auditor);
        // Reflectively set the generated ID and timestamp for test assertions
        try {
            var auditLogIdField = AuditLog.class.getDeclaredField("auditLogId");
            auditLogIdField.setAccessible(true);
            auditLogIdField.set(log, id);

            var actionedAtField = AuditLog.class.getDeclaredField("actionedAt");
            actionedAtField.setAccessible(true);
            actionedAtField.set(log, actionedAt);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set AuditLog fields in test", e);
        }
        return log;
    }
}