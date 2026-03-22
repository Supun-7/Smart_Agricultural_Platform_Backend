package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, passwordEncoder);
    }

    @Test
    void registerUser_shouldSaveUser() {
        User user = buildUser(1L, "QA User", "qa@example.com", "pw123", Role.FARMER);
        when(passwordEncoder.encode("pw123")).thenReturn("hashed_pw123");
        when(userRepository.save(any(User.class))).thenReturn(user);

        User savedUser = userService.registerUser(user);

        assertEquals("qa@example.com", savedUser.getEmail());
        assertEquals(Role.FARMER, savedUser.getRole());
    }

    @Test
    void login_shouldReturnUserWhenPasswordMatches() {
        User user = buildUser(5L, "Login User", "login@example.com", "secret", Role.ADMIN);
        when(userRepository.findByEmail("login@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "secret")).thenReturn(true);

        Optional<User> result = userService.login("login@example.com", "secret");

        assertTrue(result.isPresent());
        assertEquals(5L, result.get().getUserId());
    }

    @Test
    void login_shouldReturnEmptyWhenPasswordDoesNotMatch() {
        User user = buildUser(5L, "Login User", "login@example.com", "secret", Role.ADMIN);
        when(userRepository.findByEmail("login@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "secret")).thenReturn(false);

        Optional<User> result = userService.login("login@example.com", "wrong");

        assertTrue(result.isEmpty());
    }

    @Test
    void login_shouldReturnEmptyWhenUserDoesNotExist() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        Optional<User> result = userService.login("missing@example.com", "secret");

        assertTrue(result.isEmpty());
    }

    private User buildUser(Long id, String fullName, String email, String passwordHash, Role role) {
        User user = new User();
        user.setUserId(id);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setRole(role);
        user.setVerificationStatus(VerificationStatus.NOT_SUBMITTED);
        return user;
    }
}
