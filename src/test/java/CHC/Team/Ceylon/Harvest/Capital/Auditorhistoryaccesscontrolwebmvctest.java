package CHC.Team.Ceylon.Harvest.Capital;

import CHC.Team.Ceylon.Harvest.Capital.config.WebConfig;
import CHC.Team.Ceylon.Harvest.Capital.controller.AuditorHistoryController;
import CHC.Team.Ceylon.Harvest.Capital.dto.AuditLogResponse;
import CHC.Team.Ceylon.Harvest.Capital.enums.AuditActionType;
import CHC.Team.Ceylon.Harvest.Capital.repository.AuditLogRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.security.RoleInterceptor;
import CHC.Team.Ceylon.Harvest.Capital.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// AC-3: a dedicated history endpoint is accessible only to authenticated auditors
// AC-5: the history is read-only — only the GET method is exposed
@WebMvcTest(AuditorHistoryController.class)
@Import({WebConfig.class, RoleInterceptor.class})
class AuditorHistoryAccessControlWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditLogService auditLogService;

    @MockitoBean
    private AuditLogRepository auditLogRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JwtUtil jwtUtil;

    // ── AC-3: valid AUDITOR token can access the history endpoint ─────────────

    @Test
    void auditorHistory_shouldReturn200ForValidAuditorToken() throws Exception {
        stubValidTokenWithRole("AUDITOR");
        given(auditLogService.getHistoryForAuditor(anyLong()))
                .willReturn(List.of(buildSampleResponse()));

        mockMvc.perform(get("/api/auditor/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-auditor-token"))
                .andExpect(status().isOk());
    }

    // ── AC-3: FARMER role is blocked from viewing audit history ───────────────

    @Test
    void auditorHistory_shouldReturn403ForFarmerRole() throws Exception {
        stubValidTokenWithRole("FARMER");

        mockMvc.perform(get("/api/auditor/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farmer-token"))
                .andExpect(status().isForbidden());
    }

    // ── AC-3: INVESTOR role is blocked from viewing audit history ────────────

    @Test
    void auditorHistory_shouldReturn403ForInvestorRole() throws Exception {
        stubValidTokenWithRole("INVESTOR");

        mockMvc.perform(get("/api/auditor/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer investor-token"))
                .andExpect(status().isForbidden());
    }

    // ── AC-3: ADMIN role is blocked from the auditor-specific history endpoint ─

    @Test
    void auditorHistory_shouldReturn403ForAdminRole() throws Exception {
        stubValidTokenWithRole("ADMIN");

        mockMvc.perform(get("/api/auditor/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
                .andExpect(status().isForbidden());
    }

    // ── AC-3: missing Authorization header yields 401 (unauthenticated) ──────

    @Test
    void auditorHistory_shouldReturn401WhenAuthorizationHeaderIsMissing() throws Exception {
        mockMvc.perform(get("/api/auditor/history"))
                .andExpect(status().isUnauthorized());
    }

    // ── AC-3: expired or invalid token yields 401 ────────────────────────────

    @Test
    void auditorHistory_shouldReturn401WhenTokenIsInvalid() throws Exception {
        given(jwtUtil.validateToken(anyString())).willReturn(false);

        mockMvc.perform(get("/api/auditor/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired-or-bad-token"))
                .andExpect(status().isUnauthorized());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void stubValidTokenWithRole(String role) {
        given(jwtUtil.validateToken(anyString())).willReturn(true);
        given(jwtUtil.extractRole(anyString())).willReturn(role);
        given(jwtUtil.extractUserId(anyString())).willReturn("88");
    }

    private AuditLogResponse buildSampleResponse() {
        return new AuditLogResponse(
                7L, AuditActionType.APPROVED, 12L, "Kamal Perera", 88L,
                LocalDateTime.of(2026, 3, 25, 11, 0));
    }
}