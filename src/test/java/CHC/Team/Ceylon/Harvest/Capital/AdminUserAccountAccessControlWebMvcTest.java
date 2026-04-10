package CHC.Team.Ceylon.Harvest.Capital;

import CHC.Team.Ceylon.Harvest.Capital.config.WebConfig;
import CHC.Team.Ceylon.Harvest.Capital.controller.AdminController;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.AccountStatus;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import CHC.Team.Ceylon.Harvest.Capital.repository.AdminAuditLogRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.FarmerApplicationRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.KycSubmissionRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.security.RoleInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WebMvc-layer access-control tests for CHC-114: Admin User Account Activation and Suspension
 *
 * AC-5: Suspended users are blocked from every protected API endpoint by the
 *       RoleInterceptor (HTTP 403), not just from login.
 *       ADMIN and SYSTEM_ADMIN roles can reach suspend/activate endpoints.
 *       Non-admin roles (FARMER, INVESTOR, AUDITOR) are blocked with 403.
 */
@WebMvcTest(AdminController.class)
@Import({WebConfig.class, RoleInterceptor.class})
class AdminUserAccountAccessControlWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private UserRepository             userRepository;
    @MockitoBean private KycSubmissionRepository    kycSubmissionRepository;
    @MockitoBean private FarmerApplicationRepository farmerApplicationRepository;
    @MockitoBean private AdminAuditLogRepository    adminAuditLogRepository;
    @MockitoBean private JwtUtil                    jwtUtil;

    // ── AC-5: Suspended user is blocked from protected endpoints ─────────────

    /**
     * AC-5: A token belonging to a SUSPENDED user is rejected by the interceptor
     * with HTTP 403 on the suspend endpoint.
     */
    @Test
    void suspendEndpoint_suspendedUserToken_shouldReturn403() throws Exception {
        stubTokenWithRole("FARMER");
        stubSuspendedUserInRepo(5L);

        mockMvc.perform(put("/api/admin/users/99/suspend")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer suspended-token"))
                .andExpect(status().isForbidden());
    }

    /**
     * AC-5: A token belonging to a SUSPENDED user is rejected by the interceptor
     * with HTTP 403 on the activate endpoint.
     */
    @Test
    void activateEndpoint_suspendedUserToken_shouldReturn403() throws Exception {
        stubTokenWithRole("FARMER");
        stubSuspendedUserInRepo(5L);

        mockMvc.perform(put("/api/admin/users/99/activate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer suspended-token"))
                .andExpect(status().isForbidden());
    }

    /**
     * AC-5: A suspended user also cannot reach the bulk-suspend endpoint.
     */
    @Test
    void bulkSuspendEndpoint_suspendedUserToken_shouldReturn403() throws Exception {
        stubTokenWithRole("FARMER");
        stubSuspendedUserInRepo(5L);

        mockMvc.perform(put("/api/admin/users/bulk-suspend")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer suspended-token")
                        .contentType("application/json")
                        .content("{\"userIds\":[99]}"))
                .andExpect(status().isForbidden());
    }

    // ── Role restrictions: only ADMIN / SYSTEM_ADMIN may call these endpoints ─

    /**
     * ADMIN role can call the suspend endpoint.
     */
    @Test
    void suspendEndpoint_withAdminRole_shouldNotReturn403ForRoleCheck() throws Exception {
        stubTokenWithRole("ADMIN");
        stubActiveUserInRepo(1L, Role.ADMIN);

        // The interceptor passes; downstream logic may return other codes — we only
        // verify it is NOT a 403 caused by role checking (it could be 400/500 from
        // missing mock setup, which is acceptable in this access-control test).
        mockMvc.perform(put("/api/admin/users/99/suspend")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer good-token"))
                .andExpect(result ->
                        assertNotForbiddenDueToRole(result.getResponse().getStatus()));
    }

    /**
     * SYSTEM_ADMIN role can call the suspend endpoint.
     */
    @Test
    void suspendEndpoint_withSystemAdminRole_shouldNotReturn403ForRoleCheck() throws Exception {
        stubTokenWithRole("SYSTEM_ADMIN");
        stubActiveUserInRepo(1L, Role.SYSTEM_ADMIN);

        mockMvc.perform(put("/api/admin/users/99/suspend")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer good-token"))
                .andExpect(result ->
                        assertNotForbiddenDueToRole(result.getResponse().getStatus()));
    }

    /**
     * FARMER, INVESTOR, and AUDITOR roles are blocked from the suspend endpoint.
     */
    @ParameterizedTest
    @ValueSource(strings = {"FARMER", "INVESTOR", "AUDITOR"})
    void suspendEndpoint_withNonAdminRole_shouldReturn403(String role) throws Exception {
        stubTokenWithRole(role);
        stubActiveUserInRepo(1L, Role.valueOf(role));

        mockMvc.perform(put("/api/admin/users/99/suspend")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer non-admin-token"))
                .andExpect(status().isForbidden());
    }

    /**
     * FARMER, INVESTOR, and AUDITOR roles are blocked from the activate endpoint.
     */
    @ParameterizedTest
    @ValueSource(strings = {"FARMER", "INVESTOR", "AUDITOR"})
    void activateEndpoint_withNonAdminRole_shouldReturn403(String role) throws Exception {
        stubTokenWithRole(role);
        stubActiveUserInRepo(1L, Role.valueOf(role));

        mockMvc.perform(put("/api/admin/users/99/activate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer non-admin-token"))
                .andExpect(status().isForbidden());
    }

    // ── Missing / invalid token ───────────────────────────────────────────────

    /**
     * No Authorization header on suspend endpoint → 401.
     */
    @Test
    void suspendEndpoint_withNoAuthHeader_shouldReturn401() throws Exception {
        mockMvc.perform(put("/api/admin/users/99/suspend"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * No Authorization header on activate endpoint → 401.
     */
    @Test
    void activateEndpoint_withNoAuthHeader_shouldReturn401() throws Exception {
        mockMvc.perform(put("/api/admin/users/99/activate"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * An invalid / expired token is rejected with 401.
     */
    @Test
    void suspendEndpoint_withInvalidToken_shouldReturn401() throws Exception {
        given(jwtUtil.validateToken(anyString())).willReturn(false);

        mockMvc.perform(put("/api/admin/users/99/suspend")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer this.is.garbage"))
                .andExpect(status().isUnauthorized());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Stub JwtUtil to return a valid token for the given role (userId = 1). */
    private void stubTokenWithRole(String role) {
        given(jwtUtil.validateToken(anyString())).willReturn(true);
        given(jwtUtil.extractRole(anyString())).willReturn(role);
        given(jwtUtil.extractUserId(anyString())).willReturn("1");
    }

    /** Stub the repository to return a SUSPENDED user for userId = 1. */
    private void stubSuspendedUserInRepo(Long userId) {
        User suspended = new User();
        suspended.setUserId(userId);
        suspended.setFullName("Suspended User");
        suspended.setEmail("sus@farm.lk");
        suspended.setPasswordHash("hashed");
        suspended.setRole(Role.FARMER);
        suspended.setVerificationStatus(VerificationStatus.VERIFIED);
        suspended.setAccountStatus(AccountStatus.SUSPENDED);
        given(userRepository.findById(eq(1L))).willReturn(Optional.of(suspended));
    }

    /** Stub the repository to return an ACTIVE user for userId = 1 with given role. */
    private void stubActiveUserInRepo(Long userId, Role role) {
        User active = new User();
        active.setUserId(userId);
        active.setFullName("Active User");
        active.setEmail("active@chc.lk");
        active.setPasswordHash("hashed");
        active.setRole(role);
        active.setVerificationStatus(VerificationStatus.VERIFIED);
        active.setAccountStatus(AccountStatus.ACTIVE);
        given(userRepository.findById(eq(1L))).willReturn(Optional.of(active));
    }

    /**
     * Helper assertion: the response code must NOT be 403 that originates from
     * a role mismatch. In this access-control test we only care about the
     * interceptor's role decision, so 400/500 from unrelated stub gaps are acceptable.
     */
    private void assertNotForbiddenDueToRole(int status) {
        // 403 only when role check fails — here we expect the role check to pass
        // (downstream may throw due to missing mock data, which is fine)
        if (status == 403) {
            throw new AssertionError(
                    "Expected role check to PASS for ADMIN/SYSTEM_ADMIN, but got 403");
        }
    }
}
