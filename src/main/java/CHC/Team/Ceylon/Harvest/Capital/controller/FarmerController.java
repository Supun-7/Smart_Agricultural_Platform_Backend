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

import java.math.BigDecimal;
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

        private Long extractUserId(String authHeader) {
                String token = authHeader.substring(7);
                return Long.parseLong(jwtUtil.extractUserId(token));
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
                                "status", user.getVerificationStatus().name()));
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
                                "applications", applications.size()));
        }

        @GetMapping("/crops")
        @RequiredRole(Role.FARMER)
        public ResponseEntity<?> getCropData(
                        @RequestHeader("Authorization") String authHeader) {
                return ResponseEntity.ok(Map.of(
                                "message", "Crop data from DOA API — coming in Story 4",
                                "source", "https://www.apiweb.doa.gov.lk"));
        }

        // ── Submit application — now accepts full bilingual form ──
        @PostMapping("/application")
        @RequiredRole(Role.FARMER)
        public ResponseEntity<?> submitFarmApplication(
                        @RequestHeader("Authorization") String authHeader,
                        @RequestBody FarmerApplicationRequest request) {
                Long userId = extractUserId(authHeader);
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                // Block if already has pending or approved application
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

                // Build application from full form data
                FarmerApplication application = new FarmerApplication();
                application.setUser(user);

                // Personal details
                application.setFarmerName(request.farmerName());
                application.setSurname(request.surname());
                application.setFamilyName(request.familyName());
                application.setAddress(request.address());
                application.setFarmAddress(request.farmAddress());
                application.setYearStarted(request.yearStarted());

                // Farm details
                application.setNicNumber(request.nicNumber());
                application.setFarmLocation(request.farmLocation());
                application.setCropTypes(request.cropTypes());
                application.setLandMeasurements(request.landMeasurements());

                if (request.landSizeAcres() != null) {
                        application.setLandSizeAcres(
                                        new BigDecimal(request.landSizeAcres().toString()));
                }

                // Document URLs from Supabase Storage
                application.setNicFrontUrl(request.nicFrontUrl());
                application.setNicBackUrl(request.nicBackUrl());
                application.setLandPhotoUrls(request.landPhotoUrls());

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
                return ResponseEntity.ok(Map.of(
                                "message", "Update request submitted — awaiting admin approval",
                                "userId", userId,
                                "details", request.getOrDefault("details", "No details provided")));
        }

        // ── Full farmer application DTO ───────────────────────────
        public record FarmerApplicationRequest(
                        // Personal details
                        String farmerName,
                        String surname,
                        String familyName,
                        String address,
                        String farmAddress,
                        Integer yearStarted,

                        // Farm details
                        String nicNumber,
                        String farmLocation,
                        Double landSizeAcres,
                        String cropTypes,
                        String landMeasurements,

                        // Document URLs — uploaded to Supabase Storage by frontend
                        String nicFrontUrl,
                        String nicBackUrl,
                        String landPhotoUrls // comma-separated URLs
        ) {
        }
}