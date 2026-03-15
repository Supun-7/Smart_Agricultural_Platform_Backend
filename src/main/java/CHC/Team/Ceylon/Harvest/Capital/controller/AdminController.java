package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.entity.FarmerApplication;
import CHC.Team.Ceylon.Harvest.Capital.entity.KycSubmission;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import CHC.Team.Ceylon.Harvest.Capital.repository.FarmerApplicationRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.KycSubmissionRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.security.RequiredRole;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    private final UserRepository userRepository;
    private final KycSubmissionRepository kycSubmissionRepository;
    private final FarmerApplicationRepository farmerApplicationRepository;
    private final JwtUtil jwtUtil;

    public AdminController(
            UserRepository userRepository,
            KycSubmissionRepository kycSubmissionRepository,
            FarmerApplicationRepository farmerApplicationRepository,
            JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.kycSubmissionRepository = kycSubmissionRepository;
        this.farmerApplicationRepository = farmerApplicationRepository;
        this.jwtUtil = jwtUtil;
    }

    private Long extractUserId(String authHeader) {
        String token = authHeader.substring(7);
        return Long.parseLong(jwtUtil.extractUserId(token));
    }

    // ── View all users ────────────────────────────────────────
    // AC-4: only Role.ADMIN can access this
    @GetMapping("/users")
    @RequiredRole(Role.ADMIN)
    public ResponseEntity<?> getAllUsers() {
        var users = userRepository.findAll();
        return ResponseEntity.ok(Map.of(
                "total", users.size(),
                "users", users.stream().map(u -> Map.of(
                        "userId", u.getUserId(),
                        "fullName", u.getFullName(),
                        "email", u.getEmail(),
                        "role", u.getRole().name(),
                        "status", u.getVerificationStatus().name())).toList()));
    }

    // ── Pending review queue (farmers + investors together) ───
    @GetMapping("/queue")
    @RequiredRole(Role.ADMIN)
    public ResponseEntity<?> getPendingQueue() {
        List<KycSubmission> pendingKyc = kycSubmissionRepository.findByStatus(VerificationStatus.PENDING);

        List<FarmerApplication> pendingFarmers = farmerApplicationRepository.findByStatus(VerificationStatus.PENDING);

        return ResponseEntity.ok(Map.of(
                "pendingKyc", pendingKyc.stream().map(k -> Map.of(
                        "id", k.getId(),
                        "userId", k.getUser().getUserId(),
                        "userName", k.getUser().getFullName(),
                        "documentUrl", k.getDocumentUrl(),
                        "submittedAt", k.getSubmittedAt().toString())).toList(),
                "pendingFarmers", pendingFarmers.stream().map(f -> Map.of(
                        "id", f.getId(),
                        "userId", f.getUser().getUserId(),
                        "userName", f.getUser().getFullName(),
                        "nicNumber", f.getNicNumber(),
                        "farmLocation", f.getFarmLocation(),
                        "submittedAt", f.getSubmittedAt().toString())).toList()));
    }

    // ── KYC: Approve ─────────────────────────────────────────
    @PutMapping("/kyc/{id}/approve")
    @RequiredRole(Role.ADMIN)
    public ResponseEntity<?> approveKyc(
            @PathVariable String id,
            @RequestHeader("Authorization") String authHeader) {
        Long adminId = extractUserId(authHeader);
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        KycSubmission kyc = kycSubmissionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("KYC submission not found"));

        // Update KYC record
        kyc.setStatus(VerificationStatus.VERIFIED);
        kyc.setReviewedAt(LocalDateTime.now());
        kyc.setReviewedBy(admin);
        kycSubmissionRepository.save(kyc);

        // Update user's verification status too
        User investor = kyc.getUser();
        investor.setVerificationStatus(VerificationStatus.VERIFIED);
        userRepository.save(investor);

        return ResponseEntity.ok(Map.of(
                "message", "KYC approved successfully",
                "userId", investor.getUserId()));
    }

    // ── KYC: Reject ──────────────────────────────────────────
    @PutMapping("/kyc/{id}/reject")
    @RequiredRole(Role.ADMIN)
    public ResponseEntity<?> rejectKyc(
            @PathVariable String id,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody ReviewRequest request) {
        Long adminId = extractUserId(authHeader);
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        KycSubmission kyc = kycSubmissionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("KYC submission not found"));

        kyc.setStatus(VerificationStatus.REJECTED);
        kyc.setRejectionReason(request.reason());
        kyc.setReviewedAt(LocalDateTime.now());
        kyc.setReviewedBy(admin);
        kycSubmissionRepository.save(kyc);

        // Update user's verification status
        User investor = kyc.getUser();
        investor.setVerificationStatus(VerificationStatus.REJECTED);
        userRepository.save(investor);

        return ResponseEntity.ok(Map.of(
                "message", "KYC rejected",
                "reason", request.reason(),
                "userId", investor.getUserId()));
    }

    // ── Farmer: Approve ───────────────────────────────────────
    @PutMapping("/farmer/{id}/approve")
    @RequiredRole(Role.ADMIN)
    public ResponseEntity<?> approveFarmerApplication(
            @PathVariable String id,
            @RequestHeader("Authorization") String authHeader) {
        Long adminId = extractUserId(authHeader);
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        FarmerApplication application = farmerApplicationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Application not found"));

        application.setStatus(VerificationStatus.VERIFIED);
        application.setReviewedAt(LocalDateTime.now());
        application.setReviewedBy(admin);
        farmerApplicationRepository.save(application);

        // Update user's verification status
        User farmer = application.getUser();
        farmer.setVerificationStatus(VerificationStatus.VERIFIED);
        userRepository.save(farmer);

        return ResponseEntity.ok(Map.of(
                "message", "Farmer application approved",
                "userId", farmer.getUserId()));
    }

    // ── Farmer: Reject ────────────────────────────────────────
    @PutMapping("/farmer/{id}/reject")
    @RequiredRole(Role.ADMIN)
    public ResponseEntity<?> rejectFarmerApplication(
            @PathVariable String id,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody ReviewRequest request) {
        Long adminId = extractUserId(authHeader);
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        FarmerApplication application = farmerApplicationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Application not found"));

        application.setStatus(VerificationStatus.REJECTED);
        application.setRejectionReason(request.reason());
        application.setReviewedAt(LocalDateTime.now());
        application.setReviewedBy(admin);
        farmerApplicationRepository.save(application);

        // Update user's verification status
        User farmer = application.getUser();
        farmer.setVerificationStatus(VerificationStatus.REJECTED);
        userRepository.save(farmer);

        return ResponseEntity.ok(Map.of(
                "message", "Farmer application rejected",
                "reason", request.reason(),
                "userId", farmer.getUserId()));
    }

    // ── Farm update request: Approve or Reject ────────────────
    @PutMapping("/update-request/{userId}/approve")
    @RequiredRole(Role.ADMIN)
    public ResponseEntity<?> approveFarmUpdateRequest(
            @PathVariable Long userId) {
        // TODO: Story 3 — implement farm_update_requests table
        // and process actual field changes here
        return ResponseEntity.ok(Map.of(
                "message", "Farm update request approved for user " + userId));
    }

    @PutMapping("/update-request/{userId}/reject")
    @RequiredRole(Role.ADMIN)
    public ResponseEntity<?> rejectFarmUpdateRequest(
            @PathVariable Long userId,
            @RequestBody ReviewRequest request) {
        // TODO: Story 3 — implement farm_update_requests table
        return ResponseEntity.ok(Map.of(
                "message", "Farm update request rejected",
                "reason", request.reason()));
    }

    // ── Shared review request DTO ─────────────────────────────
    public record ReviewRequest(String reason) {
    }
}