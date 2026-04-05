package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.service.GoogleService;
import CHC.Team.Ceylon.Harvest.Capital.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class GoogleLoginController {

    @Autowired
    private GoogleService googleService;

    @Autowired
    private UserService userService;

    @Value("${google.client.id}")
    private String clientId;

    @Value("${google.client.secret}")
    private String clientSecret;

    // ── POST /api/auth/google ─────────────────────────────────
    // Original ID token flow — kept for compatibility
    @PostMapping("/google")
    public ResponseEntity<?> loginWithGoogle(@RequestBody Map<String, String> body) {
        String googleToken   = body.get("token");
        String requestedRole = body.get("role");

        if (googleToken == null || googleToken.isBlank()) {
            return ResponseEntity.badRequest().body("Google token is required");
        }

        try {
            var payload = googleService.verifyToken(googleToken);
            String email = payload.getEmail();
            String name  = (String) payload.get("name");

            User user = userService.findByEmail(email);
            if (user == null) {
                String role = (requestedRole != null &&
                               requestedRole.equals("FARMER")) ? "FARMER" : "INVESTOR";
                user = userService.createGoogleUser(name, email, role);
            }

            String jwt = userService.generateJwt(user);

            return ResponseEntity.ok(Map.of(
                    "token", jwt,
                    "user",  Map.of(
                            "userId",             user.getUserId(),
                            "fullName",           user.getFullName(),
                            "email",              user.getEmail(),
                            "role",               user.getRole().name(),
                            "verificationStatus", user.getVerificationStatus().name()
                    )
            ));

        } catch (Exception e) {
            return ResponseEntity.status(401).body("Invalid Google token");
        }
    }

    // ── POST /api/auth/google/callback ────────────────────────
    // Redirect flow — frontend sends authorization code
    // We exchange it for an ID token using Google's token endpoint
    @PostMapping("/google/callback")
    public ResponseEntity<?> handleGoogleCallback(@RequestBody Map<String, String> body) {
        String code          = body.get("code");
        String requestedRole = body.get("role");

        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body("Authorization code is required");
        }

        try {
            // Exchange authorization code for user info
            String redirectUri = "http://localhost:5173/auth/callback";
            var userInfo = googleService.exchangeCodeForUserInfo(
                    code, clientId, clientSecret, redirectUri);

            String email = (String) userInfo.get("email");
            String name  = (String) userInfo.get("name");

            if (email == null) {
                return ResponseEntity.status(401).body("Could not get email from Google");
            }

            // Find or create user
            User user = userService.findByEmail(email);
            if (user == null) {
                String role = "FARMER".equals(requestedRole) ? "FARMER" : "INVESTOR";
                user = userService.createGoogleUser(name, email, role);
            }

            String jwt = userService.generateJwt(user);

            return ResponseEntity.ok(Map.of(
                    "token", jwt,
                    "user",  Map.of(
                            "userId",             user.getUserId(),
                            "fullName",           user.getFullName(),
                            "email",              user.getEmail(),
                            "role",               user.getRole().name(),
                            "verificationStatus", user.getVerificationStatus().name()
                    )
            ));

        } catch (Exception e) {
            return ResponseEntity.status(401)
                    .body("Google sign-in failed: " + e.getMessage());
        }
    }
}