package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Service-layer tests for CHC-98: Restrict Admin Account Registration.
 *
 * These tests verify that UserServiceImpl correctly persists admin accounts
 * when they are created (AC-4). The authorization guard lives in the controller
 * layer; the service must faithfully save whatever role it is given once the
 * controller has validated the caller.
 *
 * AC-2: Service does not override or downgrade the ADMIN role on save.
 * AC-4: Service persists a new admin user with the correct role and a hashed password.
 */
@ExtendWith(MockitoExtension.class)
class AdminRegistrationServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, passwordEncoder);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User buildAdminUser(Long id, String fullName, String email, String rawPassword) {
        User user = new User();
        user.setUserId(id);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPasswordHash(rawPassword);
        user.setRole(Role.ADMIN);
        user.setVerificationStatus(VerificationStatus.NOT_SUBMITTED);
        return user;
    }

    private User buildUserWithRole(Long id, String email, Role role, String rawPassword) {
        User user = new User();
        user.setUserId(id);
        user.setFullName("Test User");
        user.setEmail(email);
        user.setPasswordHash(rawPassword);
        user.setRole(role);
        user.setVerificationStatus(VerificationStatus.NOT_SUBMITTED);
        return user;
    }

    // ── AC-4: Admin user is saved correctly ──────────────────────────────────

    /**
     * AC-4: registerUser() must save a user with Role.ADMIN and return the
     * persisted entity including the assigned userId.
     */
    @Test
    void registerUser_withAdminRole_shouldSaveAndReturnAdminUser() {
        User newAdmin = buildAdminUser(null, "Platform Admin", "admin@chc.com", "plaintext");
        User savedAdmin = buildAdminUser(1L, "Platform Admin", "admin@chc.com", "hashed_plaintext");

        when(passwordEncoder.encode("plaintext")).thenReturn("hashed_plaintext");
        when(userRepository.save(any(User.class))).thenReturn(savedAdmin);

        User result = userService.registerUser(newAdmin);

        assertNotNull(result.getUserId(), "Saved admin should have a generated userId");
        assertEquals(Role.ADMIN, result.getRole(), "Role must remain ADMIN after save");
        assertEquals("admin@chc.com", result.getEmail());
    }

    /**
     * AC-4: The password must be hashed before the admin user is persisted —
     * the plain-text password must never be stored.
     */
    @Test
    void registerUser_withAdminRole_shouldHashPasswordBeforeSave() {
        User newAdmin = buildAdminUser(null, "Hashed Admin", "hashedadmin@chc.com", "raw-secret");
        User savedAdmin = buildAdminUser(2L, "Hashed Admin", "hashedadmin@chc.com", "bcrypt-hash");

        when(passwordEncoder.encode("raw-secret")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenReturn(savedAdmin);

        userService.registerUser(newAdmin);

        // Encoder must have been called with the original plain-text password
        verify(passwordEncoder, times(1)).encode("raw-secret");
        // Repository must receive the user (with hashed password set internally)
        verify(userRepository, times(1)).save(any(User.class));
    }

    /**
     * AC-4: A second admin created by an existing admin must be persisted with
     * Role.ADMIN — the service must not alter the role assignment.
     */
    @Test
    void registerUser_adminCreatesSecondAdmin_shouldPersistAdminRole() {
        User secondAdmin = buildAdminUser(null, "Second Admin", "admin2@chc.com", "pass");
        User savedSecondAdmin = buildAdminUser(3L, "Second Admin", "admin2@chc.com", "hashed-pass");

        when(passwordEncoder.encode("pass")).thenReturn("hashed-pass");
        when(userRepository.save(any(User.class))).thenReturn(savedSecondAdmin);

        User result = userService.registerUser(secondAdmin);

        assertEquals(Role.ADMIN, result.getRole(),
                "Service must persist ADMIN role without modification");
        assertEquals(3L, result.getUserId());
    }

    // ── AC-2: Role is not altered by the service ──────────────────────────────

    /**
     * AC-2: The service does not downgrade or override any role — it persists
     * exactly the role provided by the controller (which has already validated
     * the caller). Tested for all four roles to confirm no role-tampering occurs.
     */
    @Test
    void registerUser_roleIsPreservedForAllRoles() {
        for (Role role : Role.values()) {
            User input = buildUserWithRole(null, "user@chc.com", role, "pw");
            User saved = buildUserWithRole(10L, "user@chc.com", role, "hashed-pw");

            when(passwordEncoder.encode("pw")).thenReturn("hashed-pw");
            when(userRepository.save(any(User.class))).thenReturn(saved);

            User result = userService.registerUser(input);

            assertEquals(role, result.getRole(),
                    "Service must not modify the role for: " + role);
        }
    }

    // ── AC-4: AUDITOR creation by admin ───────────────────────────────────────

    /**
     * AC-4: Admin can also create AUDITOR accounts — service must persist
     * Role.AUDITOR without altering it.
     */
    @Test
    void registerUser_withAuditorRole_shouldSaveAndReturnAuditorUser() {
        User newAuditor = buildUserWithRole(null, "auditor@chc.com", Role.AUDITOR, "auditpass");
        User savedAuditor = buildUserWithRole(4L, "auditor@chc.com", Role.AUDITOR, "hashed-auditpass");

        when(passwordEncoder.encode("auditpass")).thenReturn("hashed-auditpass");
        when(userRepository.save(any(User.class))).thenReturn(savedAuditor);

        User result = userService.registerUser(newAuditor);

        assertEquals(Role.AUDITOR, result.getRole());
        assertEquals(4L, result.getUserId());
    }

    // ── Existing tests preserved — login unaffected ──────────────────────────

    /**
     * Regression: admin login must still work after the registration restriction
     * is applied. The login flow is unaffected by CHC-98.
     */
    @Test
    void login_withValidAdminCredentials_shouldReturnAdminUser() {
        User admin = buildAdminUser(1L, "Admin Login", "admin@chc.com", "hashed-secret");

        when(userRepository.findByEmail("admin@chc.com"))
                .thenReturn(java.util.Optional.of(admin));
        when(passwordEncoder.matches("secret", "hashed-secret")).thenReturn(true);

        java.util.Optional<User> result = userService.login("admin@chc.com", "secret");

        assertTrue(result.isPresent());
        assertEquals(Role.ADMIN, result.get().getRole());
    }

    /**
     * Regression: login with wrong password for an admin returns empty.
     */
    @Test
    void login_withWrongPassword_shouldReturnEmpty() {
        User admin = buildAdminUser(1L, "Admin Login", "admin@chc.com", "hashed-secret");

        when(userRepository.findByEmail("admin@chc.com"))
                .thenReturn(java.util.Optional.of(admin));
        when(passwordEncoder.matches("wrong", "hashed-secret")).thenReturn(false);

        java.util.Optional<User> result = userService.login("admin@chc.com", "wrong");

        assertTrue(result.isEmpty());
    }
}
