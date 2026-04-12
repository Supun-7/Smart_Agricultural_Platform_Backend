package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.dto.farmer.LandRegistrationRequest;
import CHC.Team.Ceylon.Harvest.Capital.dto.farmer.LandStatusUpdateRequest;
import CHC.Team.Ceylon.Harvest.Capital.entity.FarmerApplication;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import CHC.Team.Ceylon.Harvest.Capital.repository.FarmerApplicationRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.security.RequiredRole;
import CHC.Team.Ceylon.Harvest.Capital.service.FarmerDashboardService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/farmer", "/farmer"})
public class FarmerController {

    private final UserRepository userRepository;
    private final FarmerApplicationRepository farmerApplicationRepository;
    private final JwtUtil jwtUtil;
    private final FarmerDashboardService farmerDashboardService;
    private final String doaApiBaseUrl;

    public FarmerController(
            UserRepository userRepository,
            FarmerApplicationRepository farmerApplicationRepository,
            JwtUtil jwtUtil,
            FarmerDashboardService farmerDashboardService,
            @Value("${external.doa.api.base-url:https://www.apiweb.doa.gov.lk}") String doaApiBaseUrl) {
        this.userRepository = userRepository;
        this.farmerApplicationRepository = farmerApplicationRepository;
        this.jwtUtil = jwtUtil;
        this.farmerDashboardService = farmerDashboardService;
        this.doaApiBaseUrl = doaApiBaseUrl;
    }

    private Long extractUserId(String authHeader) {
        String token = authHeader.substring(7);
        return Long.parseLong(jwtUtil.extractUserId(token));
    }

    @GetMapping("/dashboard")
    @RequiredRole(Role.FARMER)
    public ResponseEntity<?> getFarmerDashboard(
            @RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        return ResponseEntity.ok(farmerDashboardService.getFarmerDashboard(userId));
    }

    @GetMapping("/profile")
    @RequiredRole(Role.FARMER)
    public ResponseEntity<?> getFarmerProfile(
            @RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(Map.of(
                "userId", user.getUserId(),
                "fullName", user.getFullName(),
                "email", user.getEmail(),
                "role", user.getRole().name(),
                "status", user.getVerificationStatus().name()
        ));
    }

    @GetMapping("/fields")
    @RequiredRole(Role.FARMER)
    public ResponseEntity<?> getFieldListings(
            @RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        List<FarmerApplication> applications = farmerApplicationRepository
                .findByUserUserIdOrderBySubmittedAtDesc(userId);

        return ResponseEntity.ok(Map.of(
                "message", "Field listings for farmer " + userId,
                "applications", applications.size()
        ));
    }

    @GetMapping("/crops")
    @RequiredRole(Role.FARMER)
    public ResponseEntity<?> getCropData(
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(Map.of(
                "message", "Crop data from DOA API — coming in Story 4",
                "source", doaApiBaseUrl
        ));
    }

    @PostMapping("/application")
    @RequiredRole(Role.FARMER)
    public ResponseEntity<?> submitFarmApplication(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody FarmerApplicationRequest request) {

        Long userId = extractUserId(authHeader);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

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

        FarmerApplication application = new FarmerApplication();
        application.setUser(user);
        application.setFarmerName(request.farmerName());
        application.setSurname(request.surname());
        application.setFamilyName(request.familyName());
        application.setAddress(request.address());
        application.setFarmAddress(request.farmAddress());
        application.setYearStarted(request.yearStarted());
        application.setNicNumber(request.nicNumber());
        application.setFarmLocation(request.farmLocation());
        application.setCropTypes(request.cropTypes());
        application.setLandMeasurements(request.landMeasurements());
        application.setNicFrontUrl(request.nicFrontUrl());
        application.setNicBackUrl(request.nicBackUrl());
        application.setLandPhotoUrls(request.landPhotoUrls());
        application.setStatus(VerificationStatus.PENDING);

        if (request.landSizeAcres() != null) {
            application.setLandSizeAcres(
                    new BigDecimal(request.landSizeAcres().toString()));
        }

        farmerApplicationRepository.save(application);

        user.setVerificationStatus(VerificationStatus.PENDING);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "message", "Application submitted successfully",
                "status", "PENDING"
        ));
    }

    @PostMapping("/lands")
    @RequiredRole(Role.FARMER)
    public ResponseEntity<?> createLandListing(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody LandRegistrationRequest request) {
        Long userId = extractUserId(authHeader);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(farmerDashboardService.createLand(userId, request));
    }

    @GetMapping("/lands")
    @RequiredRole(Role.FARMER)
    public ResponseEntity<?> getMyLandListings(
            @RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        var lands = farmerDashboardService.getFarmerLands(userId);
        return ResponseEntity.ok(Map.of(
                "lands", lands,
                "count", lands.size()
        ));
    }

    @PatchMapping("/lands/{landId}/active")
    @RequiredRole(Role.FARMER)
    public ResponseEntity<?> updateLandListingStatus(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long landId,
            @Valid @RequestBody LandStatusUpdateRequest request) {
        Long userId = extractUserId(authHeader);
        return ResponseEntity.ok(farmerDashboardService.updateLandStatus(userId, landId, request.isActive()));
    }

    @PostMapping("/update-request")
    @RequiredRole(Role.FARMER)
    public ResponseEntity<?> requestFarmUpdate(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> request) {
        Long userId = extractUserId(authHeader);
        return ResponseEntity.ok(Map.of(
                "message", "Update request submitted — awaiting admin approval",
                "userId", userId,
                "details", request.getOrDefault("details", "No details provided")
        ));
    }

    // ── GET /api/farmer/contracts ─────────────────────────────────────────
    // Returns all investments received on the farmer's lands — shown on the
    // farmer contracts page in Sinhala. Blockchain links are excluded from
    // the frontend display for farmers (handled in the UI layer).
    @GetMapping("/contracts")
    @RequiredRole(Role.FARMER)
    public ResponseEntity<?> getFarmerContracts(
            @RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        return ResponseEntity.ok(farmerDashboardService.getFarmerContracts(userId));
    }

    public record FarmerApplicationRequest(
            String farmerName,
            String surname,
            String familyName,
            String address,
            String farmAddress,
            Integer yearStarted,
            String nicNumber,
            String farmLocation,
            Double landSizeAcres,
            String cropTypes,
            String landMeasurements,
            String nicFrontUrl,
            String nicBackUrl,
            String landPhotoUrls
    ) {
    }
}
