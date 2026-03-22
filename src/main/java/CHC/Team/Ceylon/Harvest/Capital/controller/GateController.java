package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.service.GateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/gate")
@CrossOrigin(origins = "*")
public class GateController {

    private final GateService gateService;
    private final JwtUtil jwtUtil;

    public GateController(GateService gateService, JwtUtil jwtUtil) {
        this.gateService = gateService;
        this.jwtUtil = jwtUtil;
    }

    // Frontend calls this right after login
    // Sends the JWT token in the Authorization header
    @GetMapping("/check")
    public ResponseEntity<?> checkGate(
        @RequestHeader("Authorization") String authHeader
    ) {
        // Extract token from "Bearer <token>"
        String token = authHeader.substring(7);

        // Validate token first
        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(401).body("Invalid or expired token");
        }

        // Get userId from token
        Long userId = Long.parseLong(jwtUtil.extractUserId(token));

        // Ask the gate service what this user should see
        GateService.GateResponse response = gateService.checkGate(userId);

        return ResponseEntity.ok(response);
    }
}