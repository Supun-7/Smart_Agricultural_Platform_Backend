package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import CHC.Team.Ceylon.Harvest.Capital.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public UserController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    // Registration endpoint
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest request) {

        // build User from request, not directly from body
        // This prevents someone from sending role=ADMIN in the request body
        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(request.getPassword()); // UserService will hash this

        // safely parse the role from string
        try {
            Role role = Role.valueOf(request.getRole().toUpperCase());
            user.setRole(role);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body("Invalid role. Must be FARMER or INVESTOR");
        }

        // default status is NOT_SUBMITTED for new users
        user.setVerificationStatus(VerificationStatus.NOT_SUBMITTED);

        User savedUser = userService.registerUser(user);
        return ResponseEntity.ok(new UserResponse(savedUser));
    }

    // Login endpoint
    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody LoginRequest request) {
        Optional<User> userOpt = userService.login(
                request.getEmail(),
                request.getPassword());

        if (userOpt.isPresent()) {
            User user = userOpt.get();

            // token now also carries verificationStatus
            String token = jwtUtil.generateToken(
                    user.getUserId(),
                    user.getRole().name(),
                    user.getVerificationStatus().name() // ← added
            );

            // return UserResponse not full User object (hides passwordHash)
            return ResponseEntity.ok(new AuthResponse(token, new UserResponse(user)));

        } else {
            return ResponseEntity.status(401).body("Invalid email or password");
        }
    }

    @GetMapping("/test")
    public ResponseEntity<String> testEndpoint() {
        return ResponseEntity.ok("JWT is working. You are authenticated.");
    }

    public static class RegisterRequest {
        private String fullName;
        private String email;
        private String password;
        private String role; // "FARMER" or "INVESTOR"

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }

    // Existing LoginRequest — unchanged
    public static class LoginRequest {
        private String email;
        private String password;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    // safe user data (NO passwordHash exposed)
    public static class UserResponse {
        private Long userId;
        private String fullName;
        private String email;
        private String role;
        private String verificationStatus;

        public UserResponse(User user) {
            this.userId = user.getUserId();
            this.fullName = user.getFullName();
            this.email = user.getEmail();
            this.role = user.getRole().name();
            this.verificationStatus = user.getVerificationStatus().name();
        }

        public Long getUserId() {
            return userId;
        }

        public String getFullName() {
            return fullName;
        }

        public String getEmail() {
            return email;
        }

        public String getRole() {
            return role;
        }

        public String getVerificationStatus() {
            return verificationStatus;
        }
    }

    // CHANGED: AuthResponse now uses UserResponse instead of User
    public static class AuthResponse {
        private String token;
        private UserResponse user;

        public AuthResponse(String token, UserResponse user) {
            this.token = token;
            this.user = user;
        }

        public String getToken() {
            return token;
        }

        public UserResponse getUser() {
            return user;
        }
    }
}