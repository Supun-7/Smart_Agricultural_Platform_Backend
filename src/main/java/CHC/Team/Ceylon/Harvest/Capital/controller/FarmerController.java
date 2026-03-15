package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.entity.FarmerApplication;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import CHC.Team.Ceylon.Harvest.Capital.repository.FarmerApplicationRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.security.RequiredRole;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/farmer")
@CrossOrigin(origins = "*")
public class FarmerController {

    private final UserRepository userRepository;
    private final FarmerApplicationRepository farmerApplicationRepository;
    private final JwtUtil jwtUtil;

    public FarmerController(
            UserRepository userRepository,
            FarmerApplicationRepository farmerApplicationRepository,
            JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.farmerApplicationRepository = farmerApplicationRepository;
        this.jwtUtil = jwtUtil;
    }

    // ── Helper: extract userId from token header ──────────────
    private Long extractUserId(String authHeader) {
        String token = authHeader.substring(7);
        return Long.parseLong(jwtUtil.extractUserId(token));
    }

    // AC-2: endpoint annotated with permitted role
    @GetMapping("/profile")
    @RequiredRole(Role.FARMER)
    public ResponseEntity<?> getFarmerProfile(
            @RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Return safe profile data — no password
        return ResponseEntity.ok(Map.of(
                "userId", user.getUserId(),
                "fullName", user.getFullName(),
                "email", user.getEmail(),
                "role", user.getRole().name(),
                "status", user.getVerificationStatus().name()));
    }

    @GetMapping("/fields")
    @RequiredRole(Role.FARMER)
    public ResponseEntity<?> getFieldListings(
            @RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);

        // TODO: Story 3 — fetch actual field listings from fields table
        // For now returns the farmer's application data as proof of concept
        List<FarmerApplication> applications = farmerApplicationRepository
                .findByUserUserIdOrderBySubmittedAtDesc(userId);

        return ResponseEntity.ok(Map.of(
                "message", "Field listings for farmer " + userId,
                "applications", applications.size()));
    }

    @GetMapping("/crops")
    @RequiredRole(Role.FARMER)
    public ResponseEntity<?> getCropData(
            @RequestHeader("Authorization") String authHeader) {
        // TODO: Story 4 — call DOA API (apiweb.doa.gov.lk)
        // and return real crop data for this farmer's region
        return ResponseEntity.ok(Map.of(
                "message", "Crop data from DOA API — coming in Story 4",
                "source", "https://www.apiweb.doa.gov.lk"));
    }

    @PostMapping("/application")
    @RequiredRole(Role.FARMER)
    public ResponseEntity<?> submitFarmApplication(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody FarmerApplicationRequest request) {
        Long userId = extractUserId(authHeader);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if already has a pending or approved application
        var existing = farmerApplicationRepository
                .findTopByUserUserIdOrderBySubmittedAtDesc(userId);

        if (existing.isPresent()) {
            VerificationStatus currentStatus = existing.get().getStatus();
            if (currentStatus == VerificationStatus.PENDING ||
                    currentStatus == VerificationStatus.VERIFIED) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "You already have an active application"));
            }
        }

        // Create new application
        FarmerApplication application = new FarmerApplication();
        application.setUser(user);
        application.setNicNumber(request.nicNumber());
        application.setFarmLocation(request.farmLocation());
        application.setLandSizeAcres(request.landSizeAcres());
        application.setCropTypes(request.cropTypes());
        application.setStatus(VerificationStatus.PENDING);

        farmerApplicationRepository.save(application);

        return ResponseEntity.ok(Map.of(
                "message", "Application submitted successfully",
                "status", "PENDING"));
    }

    @PostMapping("/update-request")
    @RequiredRole(Role.FARMER)
    public ResponseEntity<?> requestFarmUpdate(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> request) {
        Long userId = extractUserId(authHeader);

        // TODO: Story 3 — save update request to a farm_update_requests table
        // Admin will see this in their queue and approve/deny the changes
        return ResponseEntity.ok(Map.of(
                "message", "Update request submitted — awaiting admin approval",
                "userId", userId,
                "details", request.getOrDefault("details", "No details provided")));
    }

    // ── Request DTO using Java Record ─────────────────────────
    public record FarmerApplicationRequest(
            String nicNumber,
            String farmLocation,
            java.math.BigDecimal landSizeAcres,
            String cropTypes) {
    }
}