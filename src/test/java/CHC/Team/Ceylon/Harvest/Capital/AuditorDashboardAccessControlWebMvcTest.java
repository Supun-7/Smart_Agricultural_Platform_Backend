package CHC.Team.Ceylon.Harvest.Capital;

import CHC.Team.Ceylon.Harvest.Capital.config.WebConfig;
import CHC.Team.Ceylon.Harvest.Capital.controller.AuditorController;
import CHC.Team.Ceylon.Harvest.Capital.entity.FarmerApplication;
import CHC.Team.Ceylon.Harvest.Capital.entity.KycSubmission;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import CHC.Team.Ceylon.Harvest.Capital.repository.FarmerApplicationRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.KycSubmissionRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.security.RoleInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// AC-1: /auditor/dashboard is the correct endpoint and responds to authorised AUDITOR tokens
// AC-4: Loading and error states — 401 (no token), 403 (wrong role), 200 (happy path)
@WebMvcTest(AuditorController.class)
@Import({WebConfig.class, RoleInterceptor.class})
class AuditorDashboardAccessControlWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private FarmerApplicationRepository farmerApplicationRepository;

    @MockitoBean
    private KycSubmissionRepository kycSubmissionRepository;

    @MockitoBean
    private JwtUtil jwtUtil;

    // ── AC-1 + AC-4: AUDITOR token reaches the dashboard and gets 200 ─────────
    @Test
    void auditorDashboard_shouldReturn200ForValidAuditorToken() throws Exception {
        stubValidTokenWithRole("AUDITOR");
        given(kycSubmissionRepository.findByStatus(VerificationStatus.PENDING))
                .willReturn(List.of());
        given(farmerApplicationRepository.findByStatus(VerificationStatus.PENDING))
                .willReturn(List.of());

        mockMvc.perform(get("/api/auditor/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-auditor-token"))
                .andExpect(status().isOk());
    }

    // ── AC-4: FARMER role receives 403 — wrong role, dashboard blocked ────────
    @Test
    void auditorDashboard_shouldReturn403ForFarmerRole() throws Exception {
        stubValidTokenWithRole("FARMER");

        mockMvc.perform(get("/api/auditor/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer farmer-token"))
                .andExpect(status().isForbidden());
    }

    // ── AC-4: INVESTOR role receives 403 — wrong role, dashboard blocked ──────
    @Test
    void auditorDashboard_shouldReturn403ForInvestorRole() throws Exception {
        stubValidTokenWithRole("INVESTOR");

        mockMvc.perform(get("/api/auditor/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer investor-token"))
                .andExpect(status().isForbidden());
    }

    // ── AC-4: ADMIN role receives 403 — auditor endpoint is role-specific ─────
    @Test
    void auditorDashboard_shouldReturn403ForAdminRole() throws Exception {
        stubValidTokenWithRole("ADMIN");

        mockMvc.perform(get("/api/auditor/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
                .andExpect(status().isForbidden());
    }

    // ── AC-4: Missing Authorization header yields 401 (unauthenticated) ───────
    @Test
    void auditorDashboard_shouldReturn401WhenAuthorizationHeaderIsMissing() throws Exception {
        mockMvc.perform(get("/api/auditor/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    // ── AC-4: Invalid / expired token yields 401 ─────────────────────────────
    @Test
    void auditorDashboard_shouldReturn401WhenTokenIsInvalid() throws Exception {
        given(jwtUtil.validateToken(anyString())).willReturn(false);

        mockMvc.perform(get("/api/auditor/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired-or-bad-token"))
                .andExpect(status().isUnauthorized());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private void stubValidTokenWithRole(String role) {
        given(jwtUtil.validateToken(anyString())).willReturn(true);
        given(jwtUtil.extractRole(anyString())).willReturn(role);
        given(jwtUtil.extractUserId(anyString())).willReturn("1");
    }
}
