package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.entity.AdminAuditLog;
import CHC.Team.Ceylon.Harvest.Capital.entity.FarmerApplication;
import CHC.Team.Ceylon.Harvest.Capital.entity.KycSubmission;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.AccountStatus;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import CHC.Team.Ceylon.Harvest.Capital.repository.AdminAuditLogRepository;
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
    private final AdminAuditLogRepository adminAuditLogRepository;
    private final JwtUtil jwtUtil;

    public AdminController(
            UserRepository userRepository,
            KycSubmissionRepository kycSubmissionRepository,
            FarmerApplicationRepository farmerApplicationRepository,
            AdminAuditLogRepository adminAuditLogRepository,
            JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.kycSubmissionRepository = kycSubmissionRepository;
        this.farmerApplicationRepository = farmerApplicationRepository;
        this.adminAuditLogRepository = adminAuditLogRepository;
        this.jwtUtil = jwtUtil;
    }

    private Long extractUserId(String authHeader) {
        String token = authHeader.substring(7);
        return Long.parseLong(jwtUtil.extractUserId(token));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
    }

    private ResponseEntity<?> validateAdminAccountChange(User admin, User targetUser) {
        if (admin.getUserId().equals(targetUser.getUserId())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Nobody can change their own account status."));
        }

        boolean actorIsSystemAdmin = admin.getRole() == Role.SYSTEM_ADMIN;
        boolean actorIsAdmin = admin.getRole() == Role.ADMIN;
        boolean targetIsAdmin = targetUser.getRole() == Role.ADMIN;
        boolean targetIsSystemAdmin = targetUser.getRole() == Role.SYSTEM_ADMIN;

        if (!actorIsAdmin && !actorIsSystemAdmin) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "You are not allowed to manage user accounts."));
        }

        if (actorIsAdmin && (targetIsAdmin || targetIsSystemAdmin)) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Only system admin can manage admin accounts."));
        }

        if (targetIsSystemAdmin) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "System admin accounts cannot be changed here."));
        }

        return null;
    }

    private void logAdminAction(User admin, User targetUser, String actionType, String details) {
        adminAuditLogRepository.save(new AdminAuditLog(admin, targetUser, actionType, details));
    }

    @GetMapping("/users")
    @RequiredRole({Role.ADMIN, Role.SYSTEM_ADMIN})
    public ResponseEntity<?> getAllUsers() {
        var users = userRepository.findAll();
        return ResponseEntity.ok(Map.of(
                "total", users.size(),
                "users", users.stream().map(u -> Map.of(
                        "userId", u.getUserId(),
                        "fullName", u.getFullName(),
                        "email", u.getEmail(),
                        "role", u.getRole().name(),
                        "status", u.getVerificationStatus().name(),
                        "accountStatus", u.getAccountStatus() != null ? u.getAccountStatus().name() : "ACTIVE"
                )).toList()));
    }

    @GetMapping("/audit-logs")
    @RequiredRole({Role.ADMIN, Role.SYSTEM_ADMIN})
    public ResponseEntity<?> getAdminAuditLogs() {
        var logs = adminAuditLogRepository.findTop20ByOrderByCreatedAtDesc();
        return ResponseEntity.ok(logs.stream().map(log -> Map.of(
                "id", log.getId(),
                "actionType", log.getActionType(),
                "adminUserId", log.getAdminUser().getUserId(),
                "adminName", log.getAdminUser().getFullName(),
                "targetUserId", log.getTargetUser().getUserId(),
                "targetName", log.getTargetUser().getFullName(),
                "targetEmail", log.getTargetUser().getEmail(),
                "details", log.getDetails(),
                "createdAt", log.getCreatedAt()
        )));
    }

    @PutMapping("/users/{userId}/suspend")
    @RequiredRole({Role.ADMIN, Role.SYSTEM_ADMIN})
    public ResponseEntity<?> suspendUser(
            @PathVariable Long userId,
            @RequestHeader("Authorization") String authHeader) {

        User admin = requireUser(extractUserId(authHeader));
        User user = requireUser(userId);

        ResponseEntity<?> validationFailure = validateAdminAccountChange(admin, user);
        if (validationFailure != null) {
            return validationFailure;
        }
        if (user.getAccountStatus() == AccountStatus.SUSPENDED) {
            return ResponseEntity.badRequest().body(Map.of("error", "Account is already suspended."));
        }

        user.setAccountStatus(AccountStatus.SUSPENDED);
        userRepository.save(user);
        logAdminAction(admin, user, "SUSPEND_USER", "Suspended account");

        return ResponseEntity.ok(Map.of(
                "message", "Account suspended successfully.",
                "userId", user.getUserId(),
                "accountStatus", user.getAccountStatus().name()
        ));
    }

    @PutMapping("/users/{userId}/activate")
    @RequiredRole({Role.ADMIN, Role.SYSTEM_ADMIN})
    public ResponseEntity<?> activateUser(
            @PathVariable Long userId,
            @RequestHeader("Authorization") String authHeader) {

        User admin = requireUser(extractUserId(authHeader));
        User user = requireUser(userId);

        ResponseEntity<?> validationFailure = validateAdminAccountChange(admin, user);
        if (validationFailure != null) {
            return validationFailure;
        }
        if (user.getAccountStatus() == AccountStatus.ACTIVE) {
            return ResponseEntity.badRequest().body(Map.of("error", "Account is already active."));
        }

        user.setAccountStatus(AccountStatus.ACTIVE);
        userRepository.save(user);
        logAdminAction(admin, user, "ACTIVATE_USER", "Activated account");

        return ResponseEntity.ok(Map.of(
                "message", "Account activated successfully.",
                "userId", user.getUserId(),
                "accountStatus", user.getAccountStatus().name()
        ));
    }

    @PutMapping("/users/bulk-suspend")
    @RequiredRole({Role.ADMIN, Role.SYSTEM_ADMIN})
    public ResponseEntity<?> bulkSuspendUsers(
            @RequestBody BulkAccountActionRequest request,
            @RequestHeader("Authorization") String authHeader) {
        User admin = requireUser(extractUserId(authHeader));
        int updated = 0;
        for (Long userId : request.userIds()) {
            User targetUser = requireUser(userId);
            ResponseEntity<?> validationFailure = validateAdminAccountChange(admin, targetUser);
            if (validationFailure != null) {
                continue;
            }
            if (targetUser.getAccountStatus() != AccountStatus.SUSPENDED) {
                targetUser.setAccountStatus(AccountStatus.SUSPENDED);
                userRepository.save(targetUser);
                logAdminAction(admin, targetUser, "BULK_SUSPEND_USER", "Bulk suspended account");
                updated++;
            }
        }
        return ResponseEntity.ok(Map.of("message", "Bulk suspend completed.", "updatedCount", updated));
    }

    @PutMapping("/users/bulk-activate")
    @RequiredRole({Role.ADMIN, Role.SYSTEM_ADMIN})
    public ResponseEntity<?> bulkActivateUsers(
            @RequestBody BulkAccountActionRequest request,
            @RequestHeader("Authorization") String authHeader) {
        User admin = requireUser(extractUserId(authHeader));
        int updated = 0;
        for (Long userId : request.userIds()) {
            User targetUser = requireUser(userId);
            ResponseEntity<?> validationFailure = validateAdminAccountChange(admin, targetUser);
            if (validationFailure != null) {
                continue;
            }
            if (targetUser.getAccountStatus() != AccountStatus.ACTIVE) {
                targetUser.setAccountStatus(AccountStatus.ACTIVE);
                userRepository.save(targetUser);
                logAdminAction(admin, targetUser, "BULK_ACTIVATE_USER", "Bulk activated account");
                updated++;
            }
        }
        return ResponseEntity.ok(Map.of("message", "Bulk activate completed.", "updatedCount", updated));
    }

    @GetMapping("/queue")
    @RequiredRole({Role.ADMIN, Role.SYSTEM_ADMIN})
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

    @PutMapping("/kyc/{id}/approve")
    @RequiredRole({Role.ADMIN, Role.SYSTEM_ADMIN})
    public ResponseEntity<?> approveKyc(
            @PathVariable String id,
            @RequestHeader("Authorization") String authHeader) {
        Long adminId = extractUserId(authHeader);
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        KycSubmission kyc = kycSubmissionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("KYC submission not found"));

        kyc.setStatus(VerificationStatus.VERIFIED);
        kyc.setReviewedAt(LocalDateTime.now());
        kyc.setReviewedBy(admin);
        kycSubmissionRepository.save(kyc);

        User investor = kyc.getUser();
        investor.setVerificationStatus(VerificationStatus.VERIFIED);
        userRepository.save(investor);

        return ResponseEntity.ok(Map.of(
                "message", "KYC approved successfully",
                "userId", investor.getUserId()));
    }

    @PutMapping("/kyc/{id}/reject")
    @RequiredRole({Role.ADMIN, Role.SYSTEM_ADMIN})
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

        User investor = kyc.getUser();
        investor.setVerificationStatus(VerificationStatus.REJECTED);
        userRepository.save(investor);

        return ResponseEntity.ok(Map.of(
                "message", "KYC rejected",
                "reason", request.reason(),
                "userId", investor.getUserId()));
    }

    @PutMapping("/farmer/{id}/approve")
    @RequiredRole({Role.ADMIN, Role.SYSTEM_ADMIN})
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

        User farmer = application.getUser();
        farmer.setVerificationStatus(VerificationStatus.VERIFIED);
        userRepository.save(farmer);

        return ResponseEntity.ok(Map.of(
                "message", "Farmer application approved",
                "userId", farmer.getUserId()));
    }

    @PutMapping("/farmer/{id}/reject")
    @RequiredRole({Role.ADMIN, Role.SYSTEM_ADMIN})
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

        User farmer = application.getUser();
        farmer.setVerificationStatus(VerificationStatus.REJECTED);
        userRepository.save(farmer);

        return ResponseEntity.ok(Map.of(
                "message", "Farmer application rejected",
                "reason", request.reason(),
                "userId", farmer.getUserId()));
    }

    @PutMapping("/update-request/{userId}/approve")
    @RequiredRole({Role.ADMIN, Role.SYSTEM_ADMIN})
    public ResponseEntity<?> approveFarmUpdateRequest(@PathVariable Long userId) {
        return ResponseEntity.ok(Map.of(
                "message", "Farm update request approved for user " + userId));
    }

    @PutMapping("/update-request/{userId}/reject")
    @RequiredRole({Role.ADMIN, Role.SYSTEM_ADMIN})
    public ResponseEntity<?> rejectFarmUpdateRequest(
            @PathVariable Long userId,
            @RequestBody ReviewRequest request) {
        return ResponseEntity.ok(Map.of(
                "message", "Farm update request rejected",
                "reason", request.reason()));
    }

    public record ReviewRequest(String reason) {}
    public record BulkAccountActionRequest(List<Long> userIds) {}
}
