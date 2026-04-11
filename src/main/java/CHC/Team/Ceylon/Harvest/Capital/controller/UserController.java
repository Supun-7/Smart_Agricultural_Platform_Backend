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
import CHC.Team.Ceylon.Harvest.Capital.service.OtpService;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final OtpService otpService;

    public UserController(UserService userService, JwtUtil jwtUtil, OtpService otpService) {
        this.userService = userService;
        this.jwtUtil     = jwtUtil;
        this.otpService  = otpService;
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
    // ── POST /api/users/login ─────────────────────────────────────────────────
    // Step 1 of 2: Validate credentials, then send OTP — no JWT issued yet.
    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody LoginRequest request) {
        try {
            Optional<User> userOpt = userService.login(
                    request.getEmail(),
                    request.getPassword());

            if (userOpt.isPresent()) {
                // Credentials are valid — generate and email OTP (AC-1)
                otpService.generateAndSendOtp(request.getEmail());
                return ResponseEntity.ok(
                        new AuthResponse.OtpSentResponse("OTP sent to your email address.", request.getEmail())
                );
            } else {
                return ResponseEntity.status(401).body("Invalid email or password");
            }

        } catch (UserServiceImpl.AccountSuspendedException ex) {
            return ResponseEntity.status(403).body(ex.getMessage());
        }
    }

    // ── POST /api/users/verify-otp ────────────────────────────────────────────
    // Step 2 of 2: Validate OTP and issue JWT on success.
    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody AuthResponse.OtpVerifyRequest request) {
        // AC-3, AC-4: Validate the submitted OTP
        boolean valid = otpService.verifyOtp(request.getEmail(), request.getOtp());

        if (!valid) {
            // AC-4: Clear error for invalid or expired OTP
            return ResponseEntity.status(401)
                    .body("Invalid or expired OTP. Please try again or request a new one.");
        }

        // OTP is valid — look up the user and issue JWT (AC-7, AC-8)
        User user = userService.findByEmail(request.getEmail());
        if (user == null) {
            return ResponseEntity.status(404).body("User not found.");
        }

        String token = jwtUtil.generateToken(
                user.getUserId(),
                user.getRole().name(),
                user.getVerificationStatus().name()
        );

        return ResponseEntity.ok(new AuthResponse(token, new UserResponse(user)));
    }

    // ── POST /api/users/resend-otp ────────────────────────────────────────────
    // AC-5: Resend OTP with rate limiting.
    @PostMapping("/resend-otp")
    public ResponseEntity<?> resendOtp(@RequestBody AuthResponse.ResendOtpRequest request) {
        // Verify the email belongs to a real user before sending anything
        User user = userService.findByEmail(request.getEmail());
        if (user == null) {
            return ResponseEntity.status(404).body("No account found with that email.");
        }

        try {
            otpService.resendOtp(request.getEmail());
            return ResponseEntity.ok("A new OTP has been sent to your email address.");
        } catch (OtpService.RateLimitExceededException ex) {
            // AC-5: Rate limit hit
            return ResponseEntity.status(429).body(ex.getMessage());
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

        // ── OTP-related DTOs ──────────────────────────────────────────────────────

        public static class OtpSentResponse {
            private String message;
            private String email;

            public OtpSentResponse(String message, String email) {
                this.message = message;
                this.email   = email;
            }

            public String getMessage() { return message; }
            public String getEmail()   { return email; }
        }

        public static class OtpVerifyRequest {
            private String email;
            private String otp;

            public String getEmail() { return email; }
            public void setEmail(String v) { this.email = v; }
            public String getOtp() { return otp; }
            public void setOtp(String v) { this.otp = v; }
        }

        public static class ResendOtpRequest {
            private String email;

            public String getEmail() { return email; }
            public void setEmail(String v) { this.email = v; }
        }
    }
}
