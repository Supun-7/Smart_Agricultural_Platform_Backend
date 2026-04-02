package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.entity.AdminAuditLog;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.AccountStatus;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import CHC.Team.Ceylon.Harvest.Capital.repository.AdminAuditLogRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.FarmerApplicationRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.KycSubmissionRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-layer tests for CHC-114: Admin User Account Activation and Suspension
 *
 * AC-1: GET /api/admin/users returns each user's accountStatus field (ACTIVE/SUSPENDED).
 * AC-2: PUT /api/admin/users/{id}/suspend suspends an active account → 200.
 * AC-3: PUT /api/admin/users/{id}/activate reactivates a suspended account → 200.
 * AC-4: Suspending an already-suspended account → 400.
 *       Activating an already-active account → 400.
 * AC-5: Admin cannot suspend/activate their own account → 400.
 * AC-6: userRepository.save() is called so the status change is persisted immediately.
 */
@ExtendWith(MockitoExtension.class)
class AdminUserAccountControllerTest {

    @Mock private UserRepository userRepository;
    @Mock private KycSubmissionRepository kycSubmissionRepository;
    @Mock private FarmerApplicationRepository farmerApplicationRepository;
    @Mock private AdminAuditLogRepository adminAuditLogRepository;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private JwtUtil jwtUtil;

    private static final String JWT_SECRET = "MDEyMzQ1Njc4OUFCQ0RFRjAxMjM0NTY3ODlBQkNERUY=";
    private static final Long   ADMIN_ID   = 1L;
    private static final Long   TARGET_ID  = 99L;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(JWT_SECRET, 3600000L);
        AdminController controller = new AdminController(
                userRepository, kycSubmissionRepository,
                farmerApplicationRepository, adminAuditLogRepository, jwtUtil);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String adminToken() {
        return "Bearer " + jwtUtil.generateToken(ADMIN_ID, "ADMIN", "VERIFIED");
    }

    private User buildUser(Long id, String name, String email, Role role, AccountStatus status) {
        User u = new User();
        u.setUserId(id);
        u.setFullName(name);
        u.setEmail(email);
        u.setPasswordHash("hashed");
        u.setRole(role);
        u.setVerificationStatus(VerificationStatus.VERIFIED);
        u.setAccountStatus(status);
        return u;
    }

    // ── AC-1: GET /api/admin/users lists accountStatus per user ──────────────

    /**
     * AC-1: User list response includes the accountStatus field set to ACTIVE.
     */
    @Test
    void getAllUsers_shouldReturnAccountStatusActiveForActiveUser() throws Exception {
        User admin  = buildUser(ADMIN_ID, "System Admin", "admin@chc.lk",  Role.ADMIN,   AccountStatus.ACTIVE);
        User farmer = buildUser(TARGET_ID, "Nuwan Farmer", "nuwan@farm.lk", Role.FARMER, AccountStatus.ACTIVE);

        when(userRepository.findAll()).thenReturn(List.of(admin, farmer));

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[1].accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.users[1].userId").value(TARGET_ID));
    }

    /**
     * AC-1: User list response includes the accountStatus field set to SUSPENDED.
     */
    @Test
    void getAllUsers_shouldReturnAccountStatusSuspendedForSuspendedUser() throws Exception {
        User admin     = buildUser(ADMIN_ID,  "System Admin",    "admin@chc.lk",   Role.ADMIN,   AccountStatus.ACTIVE);
        User suspended = buildUser(TARGET_ID, "Suspended Farmer","sus@farm.lk",    Role.FARMER,  AccountStatus.SUSPENDED);

        when(userRepository.findAll()).thenReturn(List.of(admin, suspended));

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[1].accountStatus").value("SUSPENDED"));
    }

    /**
     * AC-1: The total count is correct and all users are present in the list.
     */
    @Test
    void getAllUsers_shouldReturnTotalCountAndBothUsers() throws Exception {
        User admin    = buildUser(ADMIN_ID,  "Admin",   "admin@chc.lk",   Role.ADMIN,   AccountStatus.ACTIVE);
        User investor = buildUser(TARGET_ID, "Investor","inv@invest.lk",  Role.INVESTOR, AccountStatus.ACTIVE);

        when(userRepository.findAll()).thenReturn(List.of(admin, investor));

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.users").isArray());
    }

    // ── AC-2: Suspend an active account ──────────────────────────────────────

    /**
     * AC-2: Admin suspends an active user → 200 with SUSPENDED status in response.
     */
    @Test
    void suspendUser_activeAccount_shouldReturn200AndSuspendedStatus() throws Exception {
        User admin  = buildUser(ADMIN_ID,  "Admin",        "admin@chc.lk",  Role.ADMIN,   AccountStatus.ACTIVE);
        User target = buildUser(TARGET_ID, "Active Farmer","farmer@farm.lk",Role.FARMER,  AccountStatus.ACTIVE);

        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenReturn(target);
        when(adminAuditLogRepository.save(any(AdminAuditLog.class))).thenReturn(null);

        mockMvc.perform(put("/api/admin/users/" + TARGET_ID + "/suspend")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("SUSPENDED"))
                .andExpect(jsonPath("$.userId").value(TARGET_ID))
                .andExpect(jsonPath("$.message").value("Account suspended successfully."));
    }

    /**
     * AC-2: Suspending an active INVESTOR account succeeds.
     */
    @Test
    void suspendUser_activeInvestor_shouldReturn200() throws Exception {
        User admin    = buildUser(ADMIN_ID,  "Admin",    "admin@chc.lk",  Role.ADMIN,    AccountStatus.ACTIVE);
        User investor = buildUser(TARGET_ID, "Investor", "inv@invest.lk", Role.INVESTOR, AccountStatus.ACTIVE);

        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(investor));
        when(userRepository.save(any(User.class))).thenReturn(investor);
        when(adminAuditLogRepository.save(any(AdminAuditLog.class))).thenReturn(null);

        mockMvc.perform(put("/api/admin/users/" + TARGET_ID + "/suspend")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("SUSPENDED"));
    }

    // ── AC-3: Activate a suspended account ───────────────────────────────────

    /**
     * AC-3: Admin activates a suspended user → 200 with ACTIVE status in response.
     */
    @Test
    void activateUser_suspendedAccount_shouldReturn200AndActiveStatus() throws Exception {
        User admin  = buildUser(ADMIN_ID,  "Admin",           "admin@chc.lk",  Role.ADMIN,  AccountStatus.ACTIVE);
        User target = buildUser(TARGET_ID, "Suspended Farmer","farmer@farm.lk",Role.FARMER, AccountStatus.SUSPENDED);

        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenReturn(target);
        when(adminAuditLogRepository.save(any(AdminAuditLog.class))).thenReturn(null);

        mockMvc.perform(put("/api/admin/users/" + TARGET_ID + "/activate")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.userId").value(TARGET_ID))
                .andExpect(jsonPath("$.message").value("Account activated successfully."));
    }

    /**
     * AC-3: Reactivating a suspended INVESTOR account succeeds.
     */
    @Test
    void activateUser_suspendedInvestor_shouldReturn200() throws Exception {
        User admin    = buildUser(ADMIN_ID,  "Admin",    "admin@chc.lk",  Role.ADMIN,    AccountStatus.ACTIVE);
        User investor = buildUser(TARGET_ID, "Investor", "inv@invest.lk", Role.INVESTOR, AccountStatus.SUSPENDED);

        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(investor));
        when(userRepository.save(any(User.class))).thenReturn(investor);
        when(adminAuditLogRepository.save(any(AdminAuditLog.class))).thenReturn(null);

        mockMvc.perform(put("/api/admin/users/" + TARGET_ID + "/activate")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"));
    }

    // ── AC-2 / AC-3: Idempotency guards ──────────────────────────────────────

    /**
     * AC-2 guard: Suspending an already-suspended account → 400 Bad Request.
     */
    @Test
    void suspendUser_alreadySuspended_shouldReturn400() throws Exception {
        User admin  = buildUser(ADMIN_ID,  "Admin",           "admin@chc.lk",  Role.ADMIN,  AccountStatus.ACTIVE);
        User target = buildUser(TARGET_ID, "Suspended Farmer","farmer@farm.lk",Role.FARMER, AccountStatus.SUSPENDED);

        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));

        mockMvc.perform(put("/api/admin/users/" + TARGET_ID + "/suspend")
                        .header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Account is already suspended."));
    }

    /**
     * AC-3 guard: Activating an already-active account → 400 Bad Request.
     */
    @Test
    void activateUser_alreadyActive_shouldReturn400() throws Exception {
        User admin  = buildUser(ADMIN_ID,  "Admin",       "admin@chc.lk",  Role.ADMIN,  AccountStatus.ACTIVE);
        User target = buildUser(TARGET_ID, "Active Farmer","farmer@farm.lk",Role.FARMER, AccountStatus.ACTIVE);

        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));

        mockMvc.perform(put("/api/admin/users/" + TARGET_ID + "/activate")
                        .header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Account is already active."));
    }

    // ── Self-action guard ─────────────────────────────────────────────────────

    /**
     * AC-5 (self-action): Admin cannot suspend their own account → 400.
     */
    @Test
    void suspendUser_ownAccount_shouldReturn400() throws Exception {
        User admin = buildUser(ADMIN_ID, "Admin", "admin@chc.lk", Role.ADMIN, AccountStatus.ACTIVE);

        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));

        mockMvc.perform(put("/api/admin/users/" + ADMIN_ID + "/suspend")
                        .header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Nobody can change their own account status."));
    }

    /**
     * AC-5 (self-action): Admin cannot activate their own account → 400.
     */
    @Test
    void activateUser_ownAccount_shouldReturn400() throws Exception {
        User admin = buildUser(ADMIN_ID, "Admin", "admin@chc.lk", Role.ADMIN, AccountStatus.ACTIVE);
        admin.setAccountStatus(AccountStatus.SUSPENDED); // edge: even if suspended

        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));

        mockMvc.perform(put("/api/admin/users/" + ADMIN_ID + "/activate")
                        .header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Nobody can change their own account status."));
    }

    // ── Role restriction: ADMIN cannot manage other ADMIN accounts ─────────────

    /**
     * An ADMIN cannot suspend another ADMIN (only SYSTEM_ADMIN can) → 403.
     */
    @Test
    void suspendUser_adminTargetingAnotherAdmin_shouldReturn403() throws Exception {
        User actorAdmin  = buildUser(ADMIN_ID,  "Admin 1", "admin1@chc.lk", Role.ADMIN, AccountStatus.ACTIVE);
        User targetAdmin = buildUser(TARGET_ID, "Admin 2", "admin2@chc.lk", Role.ADMIN, AccountStatus.ACTIVE);

        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(actorAdmin));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(targetAdmin));

        mockMvc.perform(put("/api/admin/users/" + TARGET_ID + "/suspend")
                        .header("Authorization", adminToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Only system admin can manage admin accounts."));
    }

    // ── AC-6: Persistence — save() is called on status change ────────────────

    /**
     * AC-6: userRepository.save() is invoked exactly once when suspending a user,
     * confirming the status is persisted to the Users table immediately.
     */
    @Test
    void suspendUser_shouldCallRepositorySaveOnce() throws Exception {
        User admin  = buildUser(ADMIN_ID,  "Admin",  "admin@chc.lk",  Role.ADMIN,  AccountStatus.ACTIVE);
        User target = buildUser(TARGET_ID, "Farmer", "farmer@farm.lk",Role.FARMER, AccountStatus.ACTIVE);

        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenReturn(target);
        when(adminAuditLogRepository.save(any(AdminAuditLog.class))).thenReturn(null);

        mockMvc.perform(put("/api/admin/users/" + TARGET_ID + "/suspend")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk());

        verify(userRepository, times(1)).save(target);
    }

    /**
     * AC-6: userRepository.save() is invoked exactly once when activating a user,
     * confirming the status is persisted to the Users table immediately.
     */
    @Test
    void activateUser_shouldCallRepositorySaveOnce() throws Exception {
        User admin  = buildUser(ADMIN_ID,  "Admin",  "admin@chc.lk",  Role.ADMIN,  AccountStatus.ACTIVE);
        User target = buildUser(TARGET_ID, "Farmer", "farmer@farm.lk",Role.FARMER, AccountStatus.SUSPENDED);

        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenReturn(target);
        when(adminAuditLogRepository.save(any(AdminAuditLog.class))).thenReturn(null);

        mockMvc.perform(put("/api/admin/users/" + TARGET_ID + "/activate")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk());

        verify(userRepository, times(1)).save(target);
    }

    // ── Audit log is created on each status change ────────────────────────────

    /**
     * An audit log entry is saved when a user is suspended.
     */
    @Test
    void suspendUser_shouldSaveAuditLogEntry() throws Exception {
        User admin  = buildUser(ADMIN_ID,  "Admin",  "admin@chc.lk",  Role.ADMIN,  AccountStatus.ACTIVE);
        User target = buildUser(TARGET_ID, "Farmer", "farmer@farm.lk",Role.FARMER, AccountStatus.ACTIVE);

        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenReturn(target);
        when(adminAuditLogRepository.save(any(AdminAuditLog.class))).thenReturn(null);

        mockMvc.perform(put("/api/admin/users/" + TARGET_ID + "/suspend")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk());

        verify(adminAuditLogRepository, times(1)).save(any(AdminAuditLog.class));
    }

    /**
     * An audit log entry is saved when a user is activated.
     */
    @Test
    void activateUser_shouldSaveAuditLogEntry() throws Exception {
        User admin  = buildUser(ADMIN_ID,  "Admin",  "admin@chc.lk",  Role.ADMIN,  AccountStatus.ACTIVE);
        User target = buildUser(TARGET_ID, "Farmer", "farmer@farm.lk",Role.FARMER, AccountStatus.SUSPENDED);

        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenReturn(target);
        when(adminAuditLogRepository.save(any(AdminAuditLog.class))).thenReturn(null);

        mockMvc.perform(put("/api/admin/users/" + TARGET_ID + "/activate")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk());

        verify(adminAuditLogRepository, times(1)).save(any(AdminAuditLog.class));
    }
}
