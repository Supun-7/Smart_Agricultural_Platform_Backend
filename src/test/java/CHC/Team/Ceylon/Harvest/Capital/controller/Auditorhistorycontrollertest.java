package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.dto.AuditLogResponse;
import CHC.Team.Ceylon.Harvest.Capital.enums.AuditActionType;
import CHC.Team.Ceylon.Harvest.Capital.exception.GlobalExceptionHandler;
import CHC.Team.Ceylon.Harvest.Capital.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// AC-3: the auditor can view their full audit history from a dedicated history page
// AC-4: entries are sorted with the most recent action displayed first
// AC-5: the history is read-only and cannot be edited (no POST/PUT/DELETE endpoints)
@ExtendWith(MockitoExtension.class)
class AuditorHistoryControllerTest {

    @Mock
    private AuditLogService auditLogService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuditorHistoryController controller = new AuditorHistoryController(auditLogService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── AC-3: GET /api/auditor/history returns count and items for the auditor ──

    @Test
    void getAuditHistory_shouldReturnCountAndItemsForAuthenticatedAuditor() throws Exception {
        AuditLogResponse entry1 = new AuditLogResponse(
                7L, AuditActionType.APPROVED, 12L, "Kamal Perera", 88L,
                LocalDateTime.of(2026, 3, 25, 11, 0));

        AuditLogResponse entry2 = new AuditLogResponse(
                6L, AuditActionType.REJECTED, 10L, "Nuwan Perera", 88L,
                LocalDateTime.of(2026, 3, 24, 9, 30));

        when(auditLogService.getHistoryForAuditor(88L)).thenReturn(List.of(entry1, entry2));

        mockMvc.perform(get("/api/auditor/history")
                        .requestAttr("userId", 88L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.items.length()").value(2));

        verify(auditLogService).getHistoryForAuditor(88L);
    }

    // ── AC-2: response items contain all required fields ─────────────────────

    @Test
    void getAuditHistory_shouldExposeAllRequiredFieldsInEachEntry() throws Exception {
        AuditLogResponse entry = new AuditLogResponse(
                7L, AuditActionType.APPROVED, 12L, "Kamal Perera", 88L,
                LocalDateTime.of(2026, 3, 25, 11, 0));

        when(auditLogService.getHistoryForAuditor(88L)).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/auditor/history")
                        .requestAttr("userId", 88L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].auditLogId").value(7))
                .andExpect(jsonPath("$.items[0].actionType").value("APPROVED"))
                .andExpect(jsonPath("$.items[0].milestoneId").value(12))
                .andExpect(jsonPath("$.items[0].farmerName").value("Kamal Perera"))
                .andExpect(jsonPath("$.items[0].auditorId").value(88))
                .andExpect(jsonPath("$.items[0].actionedAt").value("2026-03-25T11:00:00"));
    }

    // ── AC-4: most recent entry appears first in the items array ─────────────

    @Test
    void getAuditHistory_shouldReturnEntriesMostRecentFirst() throws Exception {
        AuditLogResponse newer = new AuditLogResponse(
                9L, AuditActionType.APPROVED, 15L, "Saman Fernando", 88L,
                LocalDateTime.of(2026, 3, 27, 14, 0));

        AuditLogResponse older = new AuditLogResponse(
                5L, AuditActionType.REJECTED, 8L, "Amali Silva", 88L,
                LocalDateTime.of(2026, 3, 20, 10, 0));

        when(auditLogService.getHistoryForAuditor(88L)).thenReturn(List.of(newer, older));

        mockMvc.perform(get("/api/auditor/history")
                        .requestAttr("userId", 88L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].auditLogId").value(9))
                .andExpect(jsonPath("$.items[0].actionedAt").value("2026-03-27T14:00:00"))
                .andExpect(jsonPath("$.items[1].auditLogId").value(5))
                .andExpect(jsonPath("$.items[1].actionedAt").value("2026-03-20T10:00:00"));
    }

    // ── AC-3: empty history returns count zero and empty items array ──────────

    @Test
    void getAuditHistory_shouldReturnEmptyListWhenAuditorHasNoHistory() throws Exception {
        when(auditLogService.getHistoryForAuditor(88L)).thenReturn(List.of());

        mockMvc.perform(get("/api/auditor/history")
                        .requestAttr("userId", 88L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    // ── AC-5: POST to /api/auditor/history is not supported (read-only) ───────

    @Test
    void postToHistoryEndpoint_shouldReturn405MethodNotAllowed() throws Exception {
        mockMvc.perform(post("/api/auditor/history")
                        .requestAttr("userId", 88L))
                .andExpect(status().isMethodNotAllowed());
    }

    // ── AC-5: PUT to /api/auditor/history is not supported (read-only) ────────

    @Test
    void putToHistoryEndpoint_shouldReturn405MethodNotAllowed() throws Exception {
        mockMvc.perform(put("/api/auditor/history")
                        .requestAttr("userId", 88L))
                .andExpect(status().isMethodNotAllowed());
    }

    // ── AC-5: DELETE to /api/auditor/history is not supported (read-only) ────

    @Test
    void deleteToHistoryEndpoint_shouldReturn405MethodNotAllowed() throws Exception {
        mockMvc.perform(delete("/api/auditor/history")
                        .requestAttr("userId", 88L))
                .andExpect(status().isMethodNotAllowed());
    }

    // ── AC-2: REJECTED entries include correct action type in response ────────

    @Test
    void getAuditHistory_shouldCorrectlyRepresentRejectedActionType() throws Exception {
        AuditLogResponse rejected = new AuditLogResponse(
                4L, AuditActionType.REJECTED, 9L, "Ruvini Jayawardena", 55L,
                LocalDateTime.of(2026, 3, 26, 8, 45));

        when(auditLogService.getHistoryForAuditor(55L)).thenReturn(List.of(rejected));

        mockMvc.perform(get("/api/auditor/history")
                        .requestAttr("userId", 55L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].actionType").value("REJECTED"))
                .andExpect(jsonPath("$.items[0].farmerName").value("Ruvini Jayawardena"))
                .andExpect(jsonPath("$.items[0].milestoneId").value(9));
    }
}