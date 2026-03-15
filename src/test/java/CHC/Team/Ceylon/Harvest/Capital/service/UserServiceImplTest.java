package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
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

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository);
    }

    @Test
    void registerUser_shouldSaveUser() {
        User user = buildUser(1L, "QA User", "qa@example.com", "pw123", "QA");
        when(userRepository.save(any(User.class))).thenReturn(user);

        User savedUser = userService.registerUser(user);

        assertEquals("qa@example.com", savedUser.getEmail());
        assertEquals("QA", savedUser.getRole());
    }

    @Test
    void login_shouldReturnUserWhenPasswordMatches() {
        User user = buildUser(5L, "Login User", "login@example.com", "secret", "ADMIN");
        when(userRepository.findByEmail("login@example.com")).thenReturn(Optional.of(user));

        Optional<User> result = userService.login("login@example.com", "secret");

        assertTrue(result.isPresent());
        assertEquals(5L, result.get().getUserId());
    }

    @Test
    void login_shouldReturnEmptyWhenPasswordDoesNotMatch() {
        User user = buildUser(5L, "Login User", "login@example.com", "secret", "ADMIN");
        when(userRepository.findByEmail("login@example.com")).thenReturn(Optional.of(user));

        Optional<User> result = userService.login("login@example.com", "wrong");

        assertTrue(result.isEmpty());
    }

    @Test
    void login_shouldReturnEmptyWhenUserDoesNotExist() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        Optional<User> result = userService.login("missing@example.com", "secret");

        assertTrue(result.isEmpty());
    }

    private User buildUser(Long id, String fullName, String email, String passwordHash, String role) {
        User user = new User();
        user.setUserId(id);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setRole(role);
        return user;
    }
}
