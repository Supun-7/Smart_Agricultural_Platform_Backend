package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import CHC.Team.Ceylon.Harvest.Capital.service.UserService;
import CHC.Team.Ceylon.Harvest.Capital.service.UserServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public UserController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(
            @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {

        Role role;
        try {
            role = Role.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body("Invalid role. Must be FARMER, INVESTOR, ADMIN or AUDITOR");
        }

        if (role == Role.ADMIN || role == Role.AUDITOR) {
            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(403).body(
                        "Forbidden: Admin credentials required to create this account type");
            }
            try {
                String token = authHeader.substring(7);
                String requesterRole = jwtUtil.extractRole(token);
                if (!"ADMIN".equals(requesterRole)) {
                    return ResponseEntity.status(403).body(
                            "Forbidden: Only admins can create ADMIN or AUDITOR accounts");
                }
                if (!jwtUtil.validateToken(token)) {
                    return ResponseEntity.status(403).body(
                            "Forbidden: Admin token is invalid or expired");
                }
            } catch (Exception e) {
                return ResponseEntity.status(403).body("Forbidden: Invalid admin token");
            }
        }

        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(request.getPassword());
        user.setRole(role);
        user.setVerificationStatus(VerificationStatus.NOT_SUBMITTED);

        User savedUser = userService.registerUser(user);
        return ResponseEntity.ok(new UserResponse(savedUser));
    }

    // ── POST /api/users/login ─────────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody LoginRequest request) {
        try {
            Optional<User> userOpt = userService.login(
                    request.getEmail(),
                    request.getPassword());

            if (userOpt.isPresent()) {
                User user = userOpt.get();
                String token = jwtUtil.generateToken(
                        user.getUserId(),
                        user.getRole().name(),
                        user.getVerificationStatus().name()
                );
                return ResponseEntity.ok(new AuthResponse(token, new UserResponse(user)));
            } else {
                return ResponseEntity.status(401).body("Invalid email or password");
            }

        } catch (UserServiceImpl.AccountSuspendedException ex) {
            // AC-4 — clear error message for suspended accounts
            return ResponseEntity.status(403).body(ex.getMessage());
        }
    }

    @GetMapping("/test")
    public ResponseEntity<String> testEndpoint() {
        return ResponseEntity.ok("JWT is working. You are authenticated.");
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    public static class RegisterRequest {
        private String fullName;
        private String email;
        private String password;
        private String role;

        public String getFullName()       { return fullName; }
        public void setFullName(String v) { this.fullName = v; }
        public String getEmail()          { return email; }
        public void setEmail(String v)    { this.email = v; }
        public String getPassword()       { return password; }
        public void setPassword(String v) { this.password = v; }
        public String getRole()           { return role; }
        public void setRole(String v)     { this.role = v; }
    }

    public static class LoginRequest {
        private String email;
        private String password;

        public String getEmail()          { return email; }
        public void setEmail(String v)    { this.email = v; }
        public String getPassword()       { return password; }
        public void setPassword(String v) { this.password = v; }
    }

    public static class UserResponse {
        private Long   userId;
        private String fullName;
        private String email;
        private String role;
        private String verificationStatus;
        private String accountStatus;

        public UserResponse(User user) {
            this.userId             = user.getUserId();
            this.fullName           = user.getFullName();
            this.email              = user.getEmail();
            this.role               = user.getRole().name();
            this.verificationStatus = user.getVerificationStatus().name();
            this.accountStatus      = user.getAccountStatus() != null
                                      ? user.getAccountStatus().name()
                                      : "ACTIVE";
        }

        public Long   getUserId()             { return userId; }
        public String getFullName()           { return fullName; }
        public String getEmail()              { return email; }
        public String getRole()               { return role; }
        public String getVerificationStatus() { return verificationStatus; }
        public String getAccountStatus()      { return accountStatus; }
    }

    public static class AuthResponse {
        private String       token;
        private UserResponse user;

        public AuthResponse(String token, UserResponse user) {
            this.token = token;
            this.user  = user;
        }

        public String       getToken() { return token; }
        public UserResponse getUser()  { return user; }
    }
}
