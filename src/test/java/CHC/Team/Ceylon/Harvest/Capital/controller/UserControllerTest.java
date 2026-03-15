package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        UserController userController = new UserController(userService, jwtUtil);
        mockMvc = MockMvcBuilders.standaloneSetup(userController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void registerUser_shouldReturnSavedUser() throws Exception {
        User request = buildUser(1L, "Sachith QA", "qa@example.com", "plain-password", "QA");
        when(userService.registerUser(any(User.class))).thenReturn(request);

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.fullName").value("Sachith QA"))
                .andExpect(jsonPath("$.email").value("qa@example.com"))
                .andExpect(jsonPath("$.role").value("QA"));
    }

    @Test
    void loginUser_withValidCredentials_shouldReturnTokenAndUser() throws Exception {
        User user = buildUser(10L, "Test User", "user@example.com", "secret123", "FARMER");
        UserController.LoginRequest loginRequest = new UserController.LoginRequest();
        loginRequest.setEmail("user@example.com");
        loginRequest.setPassword("secret123");

        when(userService.login("user@example.com", "secret123")).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.userId").value(10))
                .andExpect(jsonPath("$.user.role").value("FARMER"));
    }

    @Test
    void loginUser_withInvalidCredentials_shouldReturnUnauthorized() throws Exception {
        UserController.LoginRequest loginRequest = new UserController.LoginRequest();
        loginRequest.setEmail("wrong@example.com");
        loginRequest.setPassword("wrong-password");

        when(userService.login("wrong@example.com", "wrong-password")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testEndpoint_shouldReturnSuccessMessage() throws Exception {
        mockMvc.perform(get("/api/users/test"))
                .andExpect(status().isOk())
                .andExpect(content().string("JWT is working. You are authenticated."));
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
