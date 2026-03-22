package CHC.Team.Ceylon.Harvest.Capital;

import CHC.Team.Ceylon.Harvest.Capital.config.WebConfig;
import CHC.Team.Ceylon.Harvest.Capital.controller.UserController;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.security.RoleInterceptor;
import CHC.Team.Ceylon.Harvest.Capital.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WebMvc integration tests for CHC-98: Restrict Admin Account Registration.
 *
 * This slice loads the full Spring MVC layer (interceptors, filters, config)
 * so it validates that the actual HTTP contract is enforced end-to-end, not
 * just the controller method logic in isolation.
 *
 * AC-1: Admin role is not accessible from the public registration path without credentials.
 * AC-2: Backend enforces rejection of ADMIN role for non-admin / unauthenticated callers.
 * AC-3: Unauthenticated ADMIN registration attempt → HTTP 403.
 * AC-4: Authenticated admin token → ADMIN account creation succeeds.
 */
@WebMvcTest(UserController.class)
@Import({WebConfig.class, RoleInterceptor.class})
class AdminRegistrationAccessControlWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtUtil jwtUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Stubs ─────────────────────────────────────────────────────────────────

    private void stubAdminToken() {
        given(jwtUtil.validateToken(anyString())).willReturn(true);
        given(jwtUtil.extractRole(anyString())).willReturn("ADMIN");
        given(jwtUtil.extractUserId(anyString())).willReturn("99");
    }

    private void stubNonAdminToken(String role) {
        given(jwtUtil.validateToken(anyString())).willReturn(true);
        given(jwtUtil.extractRole(anyString())).willReturn(role);
        given(jwtUtil.extractUserId(anyString())).willReturn("50");
    }

    private String registerBody(String fullName, String email, String password, String role)
            throws Exception {
        UserController.RegisterRequest req = new UserController.RegisterRequest();
        req.setFullName(fullName);
        req.setEmail(email);
        req.setPassword(password);
        req.setRole(role);
        return objectMapper.writeValueAsString(req);
    }

    private User buildUser(Long id, String name, String email, Role role) {
        User u = new User();
        u.setUserId(id);
        u.setFullName(name);
        u.setEmail(email);
        u.setPasswordHash("hashed");
        u.setRole(role);
        u.setVerificationStatus(VerificationStatus.NOT_SUBMITTED);
        return u;
    }

    // ── AC-3: No credentials → 403 ────────────────────────────────────────────

    /**
     * AC-3: POST /api/users/register with role=ADMIN and no Authorization header
     * must return 403 Forbidden.
     */
    @Test
    void registerAdmin_noAuthHeader_returns403() throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("No Auth Admin", "noauth@chc.com", "pw", "ADMIN")))
                .andExpect(status().isForbidden());
    }

    // ── AC-2 / AC-3: Non-admin token → 403 ───────────────────────────────────

    /**
     * AC-2 / AC-3: FARMER, INVESTOR, and AUDITOR callers must be rejected with 403
     * when they attempt to register an ADMIN account.
     */
    @ParameterizedTest
    @ValueSource(strings = {"FARMER", "INVESTOR", "AUDITOR"})
    void registerAdmin_withNonAdminToken_returns403(String callerRole) throws Exception {
        stubNonAdminToken(callerRole);

        mockMvc.perform(post("/api/users/register")
                        .header("Authorization", "Bearer fake-non-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("Role Escalator", "escalate@evil.com", "pw", "ADMIN")))
                .andExpect(status().isForbidden());
    }

    // ── AC-4: Valid admin token → 200 ─────────────────────────────────────────

    /**
     * AC-4: An existing ADMIN presenting a valid token can create a new ADMIN account.
     * Response body must contain userId, role="ADMIN", and verificationStatus.
     */
    @Test
    void registerAdmin_withValidAdminToken_returns200AndAdminUser() throws Exception {
        stubAdminToken();
        given(userService.registerUser(any(User.class)))
                .willReturn(buildUser(5L, "Second Admin", "admin2@chc.com", Role.ADMIN));

        mockMvc.perform(post("/api/users/register")
                        .header("Authorization", "Bearer valid-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("Second Admin", "admin2@chc.com", "strongpw", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(5))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.email").value("admin2@chc.com"))
                .andExpect(jsonPath("$.verificationStatus").value("NOT_SUBMITTED"));
    }

    /**
     * AC-4: An existing ADMIN can also create AUDITOR accounts.
     */
    @Test
    void registerAuditor_withValidAdminToken_returns200AndAuditorUser() throws Exception {
        stubAdminToken();
        given(userService.registerUser(any(User.class)))
                .willReturn(buildUser(6L, "New Auditor", "auditor2@chc.com", Role.AUDITOR));

        mockMvc.perform(post("/api/users/register")
                        .header("Authorization", "Bearer valid-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("New Auditor", "auditor2@chc.com", "strongpw", "AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("AUDITOR"))
                .andExpect(jsonPath("$.email").value("auditor2@chc.com"));
    }

    // ── AC-1: Public roles remain accessible without admin credentials ─────────

    /**
     * AC-1: FARMER registration through the public endpoint must NOT require
     * an admin token — confirming ADMIN is not in the public role set.
     */
    @Test
    void registerFarmer_noAuthHeader_returns200() throws Exception {
        given(userService.registerUser(any(User.class)))
                .willReturn(buildUser(7L, "Public Farmer", "farmer@farm.com", Role.FARMER));

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("Public Farmer", "farmer@farm.com", "farmpass", "FARMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("FARMER"));
    }

    /**
     * AC-1: INVESTOR registration through the public endpoint must NOT require
     * an admin token.
     */
    @Test
    void registerInvestor_noAuthHeader_returns200() throws Exception {
        given(userService.registerUser(any(User.class)))
                .willReturn(buildUser(8L, "Public Investor", "investor@inv.com", Role.INVESTOR));

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("Public Investor", "investor@inv.com", "invpass", "INVESTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("INVESTOR"));
    }

    // ── Response body must never expose password hash ─────────────────────────

    /**
     * AC-4 (security): The response for a successful admin creation must not leak
     * the passwordHash field in any form.
     */
    @Test
    void registerAdmin_withValidAdminToken_responseDoesNotExposePasswordHash() throws Exception {
        stubAdminToken();
        given(userService.registerUser(any(User.class)))
                .willReturn(buildUser(9L, "Safe Admin", "safe@chc.com", Role.ADMIN));

        mockMvc.perform(post("/api/users/register")
                        .header("Authorization", "Bearer valid-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("Safe Admin", "safe@chc.com", "mypw", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }
}
