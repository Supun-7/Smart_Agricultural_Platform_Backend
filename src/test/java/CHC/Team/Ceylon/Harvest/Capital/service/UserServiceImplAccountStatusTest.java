package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.AccountStatus;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Service-layer tests for CHC-114: Admin User Account Activation and Suspension
 *
 * AC-4: A suspended user attempting to log in receives a clear
 *       "Account suspended" error — verified by AccountSuspendedException being thrown.
 * AC-6: registerUser() always sets accountStatus to ACTIVE for new accounts,
 *       confirming the default state is persisted correctly.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplAccountStatusTest {

    @Mock private UserRepository         userRepository;
    @Mock private BCryptPasswordEncoder  passwordEncoder;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, passwordEncoder);
    }

    // ── AC-4: Suspended user cannot log in ────────────────────────────────────

    /**
     * AC-4: login() throws AccountSuspendedException when the account is SUSPENDED,
     * carrying a message that mentions "suspended".
     */
    @Test
    void login_suspendedAccount_shouldThrowAccountSuspendedException() {
        User suspended = buildUser(10L, "Suspended User", "sus@farm.lk",
                                   "secret", Role.FARMER, AccountStatus.SUSPENDED);

        when(userRepository.findByEmail("sus@farm.lk")).thenReturn(Optional.of(suspended));
        when(passwordEncoder.matches("secret", "secret")).thenReturn(true);

        UserServiceImpl.AccountSuspendedException ex = assertThrows(
                UserServiceImpl.AccountSuspendedException.class,
                () -> userService.login("sus@farm.lk", "secret"));

        assertNotNull(ex.getMessage(),
                "Exception message must not be null");
        assertTrue(ex.getMessage().toLowerCase().contains("suspend"),
                "Exception message should mention 'suspend'; got: " + ex.getMessage());
    }

    /**
     * AC-4: The exception message is the clear, user-facing string the controller
     * will forward directly to the HTTP 403 response body.
     */
    @Test
    void login_suspendedAccount_exceptionMessageMatchesExpectedText() {
        User suspended = buildUser(11L, "Another User", "another@invest.lk",
                                   "pass", Role.INVESTOR, AccountStatus.SUSPENDED);

        when(userRepository.findByEmail("another@invest.lk")).thenReturn(Optional.of(suspended));
        when(passwordEncoder.matches("pass", "pass")).thenReturn(true);

        UserServiceImpl.AccountSuspendedException ex = assertThrows(
                UserServiceImpl.AccountSuspendedException.class,
                () -> userService.login("another@invest.lk", "pass"));

        assertEquals(
                "Your account has been suspended. Please contact the platform administrator.",
                ex.getMessage());
    }

    /**
     * AC-4: A suspended user with a wrong password still gets an empty Optional
     * (the password check fails before the suspension check is reached).
     * This prevents enumeration of whether an account exists at all.
     */
    @Test
    void login_suspendedAccountWithWrongPassword_shouldReturnEmpty() {
        User suspended = buildUser(12L, "Suspended Farmer", "sus2@farm.lk",
                                   "correctpass", Role.FARMER, AccountStatus.SUSPENDED);

        when(userRepository.findByEmail("sus2@farm.lk")).thenReturn(Optional.of(suspended));
        when(passwordEncoder.matches("wrongpass", "correctpass")).thenReturn(false);

        Optional<User> result = userService.login("sus2@farm.lk", "wrongpass");

        assertTrue(result.isEmpty(),
                "Incorrect password for a suspended account should return empty, not throw");
    }

    /**
     * AC-4: An ACTIVE account with correct credentials logs in normally
     * (regression — suspension logic must not break normal login flow).
     */
    @Test
    void login_activeAccountWithCorrectPassword_shouldReturnUser() {
        User active = buildUser(20L, "Active Farmer", "active@farm.lk",
                                "mypass", Role.FARMER, AccountStatus.ACTIVE);

        when(userRepository.findByEmail("active@farm.lk")).thenReturn(Optional.of(active));
        when(passwordEncoder.matches("mypass", "mypass")).thenReturn(true);

        Optional<User> result = userService.login("active@farm.lk", "mypass");

        assertTrue(result.isPresent());
        assertEquals(20L, result.get().getUserId());
    }

    /**
     * AC-4: A non-existent user still returns empty (no leakage of suspension status).
     */
    @Test
    void login_nonExistentUser_shouldReturnEmpty() {
        when(userRepository.findByEmail("ghost@farm.lk")).thenReturn(Optional.empty());

        Optional<User> result = userService.login("ghost@farm.lk", "anypass");

        assertTrue(result.isEmpty());
    }

    // ── AC-6: New registrations always start as ACTIVE ────────────────────────

    /**
     * AC-6: registerUser() sets accountStatus to ACTIVE before persisting,
     * confirming the default status written to the Users table is ACTIVE.
     */
    @Test
    void registerUser_shouldPersistWithActiveAccountStatus() {
        User incoming = buildUser(null, "New Farmer", "new@farm.lk",
                                  "plain", Role.FARMER, null);

        when(passwordEncoder.encode("plain")).thenReturn("hashed");
        when(userRepository.save(incoming)).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setUserId(30L);
            return saved;
        });

        User result = userService.registerUser(incoming);

        assertEquals(AccountStatus.ACTIVE, result.getAccountStatus(),
                "Newly registered users must default to ACTIVE");
    }

    /**
     * AC-6: Even if the caller passes AccountStatus.SUSPENDED in the request object,
     * registerUser() must override it to ACTIVE.
     */
    @Test
    void registerUser_withSuspendedStatusInRequest_shouldStillPersistAsActive() {
        User incoming = buildUser(null, "Sneaky User", "sneaky@invest.lk",
                                  "pass", Role.INVESTOR, AccountStatus.SUSPENDED);

        when(passwordEncoder.encode("pass")).thenReturn("hashed");
        when(userRepository.save(incoming)).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.registerUser(incoming);

        assertEquals(AccountStatus.ACTIVE, result.getAccountStatus(),
                "registerUser() must always set ACTIVE, regardless of incoming status");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private User buildUser(Long id, String fullName, String email,
                           String passwordHash, Role role, AccountStatus status) {
        User user = new User();
        user.setUserId(id);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setRole(role);
        user.setVerificationStatus(VerificationStatus.NOT_SUBMITTED);
        if (status != null) {
            user.setAccountStatus(status);
        }
        return user;
    }
}
