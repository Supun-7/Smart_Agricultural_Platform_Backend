package CHC.Team.Ceylon.Harvest.Capital;

import CHC.Team.Ceylon.Harvest.Capital.config.WebConfig;
import CHC.Team.Ceylon.Harvest.Capital.controller.AdminDashboardController;
import CHC.Team.Ceylon.Harvest.Capital.dto.AdminDashboardResponseDTO;
import CHC.Team.Ceylon.Harvest.Capital.dto.UserDTO;
import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.security.RoleInterceptor;
import CHC.Team.Ceylon.Harvest.Capital.service.AdminDashboardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// AC-1: GET /admin/dashboard is the correct endpoint and only reachable by ADMIN
// AC-5: Loading and error states — 401 (no/invalid token), 403 (wrong role), 200 (happy path)
@WebMvcTest(AdminDashboardController.class)
@Import({WebConfig.class, RoleInterceptor.class})
class AdminDashboardAccessControlWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminDashboardService adminDashboardService;

    @MockitoBean
    private JwtUtil jwtUtil;

    // ── AC-1 + AC-5: ADMIN token reaches the dashboard and gets 200 ───────────
    @Test
    void adminDashboard_shouldReturn200ForValidAdminToken() throws Exception {
        stubValidTokenWithRole("ADMIN");
        given(adminDashboardService.getDashboardData()).willReturn(
                new AdminDashboardResponseDTO(0, 0, BigDecimal.ZERO, List.of(), List.of()));

        mockMvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-admin-token"))
                .andExpect(status().isOk());
    }

    // ── AC-5: Non-ADMIN roles receive 403 — parameterized across all roles ────
    @ParameterizedTest
    @ValueSource(strings = {"FARMER", "INVESTOR", "AUDITOR"})
    void adminDashboard_shouldReturn403ForNonAdminRoles(String role) throws Exception {
        stubValidTokenWithRole(role);

        mockMvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer non-admin-token"))
                .andExpect(status().isForbidden());
    }

    // ── AC-5: Missing Authorization header yields 401 ─────────────────────────
    @Test
    void adminDashboard_shouldReturn401WhenAuthorizationHeaderIsMissing() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    // ── AC-5: Invalid / expired token yields 401 ─────────────────────────────
    @Test
    void adminDashboard_shouldReturn401WhenTokenIsInvalid() throws Exception {
        given(jwtUtil.validateToken(anyString())).willReturn(false);

        mockMvc.perform(get("/api/admin/dashboard")
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
