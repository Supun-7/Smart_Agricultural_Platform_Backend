package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for CHC-98: Restrict Admin Account Registration
 *
 * AC-1: Public registration form does not include an Admin role option.
 *       (Verified indirectly — the backend enforces role restrictions; no ADMIN
 *        role is accepted from unauthenticated callers.)
 * AC-2: Backend rejects any registration request with the ADMIN role unless
 *       the requester is an authenticated admin.
 * AC-3: Attempting to register as admin without admin credentials → HTTP 403.
 * AC-4: Existing admins can successfully create new admin accounts.
 */
@ExtendWith(MockitoExtension.class)
class AdminRegistrationControllerTest {

    @Mock
    private UserService userService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private JwtUtil jwtUtil;

    // Shared secret used by JwtUtil in tests (matches UserControllerTest convention)
    private static final String JWT_SECRET = "MDEyMzQ1Njc4OUFCQ0RFRjAxMjM0NTY3ODlBQkNERUY=";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(JWT_SECRET, 3600000L);
        UserController userController = new UserController(userService, jwtUtil);
        mockMvc = MockMvcBuilders.standaloneSetup(userController).build();
        objectMapper = new ObjectMapper();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Generates a real JWT token for the given role using the same JwtUtil instance. */
    private String generateTokenForRole(String role) {
        return jwtUtil.generateToken(99L, role, VerificationStatus.NOT_SUBMITTED.name());
    }

    private UserController.RegisterRequest buildRequest(String fullName, String email,
                                                        String password, String role) {
        UserController.RegisterRequest req = new UserController.RegisterRequest();
        req.setFullName(fullName);
        req.setEmail(email);
        req.setPassword(password);
        req.setRole(role);
        return req;
    }

    private User buildSavedUser(Long id, String fullName, String email, Role role) {
        User user = new User();
        user.setUserId(id);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPasswordHash("hashed");
        user.setRole(role);
        user.setVerificationStatus(VerificationStatus.NOT_SUBMITTED);
        return user;
    }

    // ── AC-1 / AC-2 / AC-3 — No token provided ───────────────────────────────

    /**
     * AC-3: Registering as ADMIN without any Authorization header → 403.
     * Also covers AC-2: backend rejects ADMIN role for unauthenticated callers.
     */
    @Test
    void registerAsAdmin_withNoAuthHeader_shouldReturn403() throws Exception {
        UserController.RegisterRequest request =
                buildRequest("Hacker", "hacker@evil.com", "pass123", "ADMIN");

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    /**
     * AC-3: Registering as ADMIN with a malformed Authorization header → 403.
     */
    @Test
    void registerAsAdmin_withMalformedAuthHeader_shouldReturn403() throws Exception {
        UserController.RegisterRequest request =
                buildRequest("Hacker", "hacker@evil.com", "pass123", "ADMIN");

        mockMvc.perform(post("/api/users/register")
                        .header("Authorization", "InvalidHeader xyz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ── AC-2 / AC-3 — Non-admin token provided ────────────────────────────────

    /**
     * AC-2 / AC-3: Non-admin roles cannot self-escalate by registering as ADMIN.
     * Covers FARMER, INVESTOR, and AUDITOR callers.
     */
    @ParameterizedTest
    @ValueSource(strings = {"FARMER", "INVESTOR", "AUDITOR"})
    void registerAsAdmin_withNonAdminToken_shouldReturn403(String callerRole) throws Exception {
        String token = generateTokenForRole(callerRole);
        UserController.RegisterRequest request =
                buildRequest("Intruder", "intruder@example.com", "pass", "ADMIN");

        mockMvc.perform(post("/api/users/register")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ── AC-4 — Admin token present ────────────────────────────────────────────

    /**
     * AC-4: An existing ADMIN can successfully create a new ADMIN account.
     * The response should include the new admin's details with role = "ADMIN".
     */
    @Test
    void registerAsAdmin_withValidAdminToken_shouldReturn200AndAdminUser() throws Exception {
        String adminToken = generateTokenForRole("ADMIN");
        UserController.RegisterRequest request =
                buildRequest("New Admin", "newadmin@chc.com", "securepass", "ADMIN");

        User savedUser = buildSavedUser(10L, "New Admin", "newadmin@chc.com", Role.ADMIN);
        when(userService.registerUser(any(User.class))).thenReturn(savedUser);

        mockMvc.perform(post("/api/users/register")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.fullName").value("New Admin"))
                .andExpect(jsonPath("$.email").value("newadmin@chc.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.verificationStatus").value("NOT_SUBMITTED"));
    }

    /**
     * AC-4: An existing ADMIN can also create AUDITOR accounts (privileged type).
     * The response should include the new auditor's details with role = "AUDITOR".
     */
    @Test
    void registerAsAuditor_withValidAdminToken_shouldReturn200AndAuditorUser() throws Exception {
        String adminToken = generateTokenForRole("ADMIN");
        UserController.RegisterRequest request =
                buildRequest("New Auditor", "auditor@chc.com", "auditpass", "AUDITOR");

        User savedUser = buildSavedUser(11L, "New Auditor", "auditor@chc.com", Role.AUDITOR);
        when(userService.registerUser(any(User.class))).thenReturn(savedUser);

        mockMvc.perform(post("/api/users/register")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("AUDITOR"))
                .andExpect(jsonPath("$.email").value("auditor@chc.com"));
    }

    // ── AC-1 — Public roles (FARMER / INVESTOR) do not require admin token ────

    /**
     * AC-1: FARMER registration via the public endpoint requires no admin token.
     * Confirms ADMIN role is not mixed into public self-service paths.
     */
    @Test
    void registerAsFarmer_withNoAuthHeader_shouldReturn200() throws Exception {
        UserController.RegisterRequest request =
                buildRequest("Farm User", "farmer@farm.com", "farmpass", "FARMER");

        User savedUser = buildSavedUser(20L, "Farm User", "farmer@farm.com", Role.FARMER);
        when(userService.registerUser(any(User.class))).thenReturn(savedUser);

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("FARMER"));
    }

    /**
     * AC-1: INVESTOR registration via the public endpoint requires no admin token.
     */
    @Test
    void registerAsInvestor_withNoAuthHeader_shouldReturn200() throws Exception {
        UserController.RegisterRequest request =
                buildRequest("Invest User", "investor@invest.com", "investpass", "INVESTOR");

        User savedUser = buildSavedUser(21L, "Invest User", "investor@invest.com", Role.INVESTOR);
        when(userService.registerUser(any(User.class))).thenReturn(savedUser);

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("INVESTOR"));
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    /**
     * AC-2 / AC-3: Submitting an invalid role string should return 400 Bad Request,
     * not 403 — the validation guard runs before the auth check.
     */
    @Test
    void registerWithInvalidRole_shouldReturn400() throws Exception {
        UserController.RegisterRequest request =
                buildRequest("Bad Role", "bad@example.com", "pass", "SUPERUSER");

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * AC-3: Attempting to register as ADMIN with an expired/invalid token → 403.
     * Uses a random garbage string that will fail JWT parsing.
     */
    @Test
    void registerAsAdmin_withExpiredToken_shouldReturn403() throws Exception {
        UserController.RegisterRequest request =
                buildRequest("Expired Caller", "expired@example.com", "pass", "ADMIN");

        mockMvc.perform(post("/api/users/register")
                        .header("Authorization", "Bearer this.is.not.a.valid.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
