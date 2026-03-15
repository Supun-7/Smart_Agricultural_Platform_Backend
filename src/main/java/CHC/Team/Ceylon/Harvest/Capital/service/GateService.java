package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.entity.KycSubmission;
import CHC.Team.Ceylon.Harvest.Capital.entity.FarmerApplication;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.repository.FarmerApplicationRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.KycSubmissionRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class GateService {

    private final UserRepository userRepository;
    private final KycSubmissionRepository kycSubmissionRepository;
    private final FarmerApplicationRepository farmerApplicationRepository;

    // Spring injects these automatically — constructor injection
    public GateService(
            UserRepository userRepository,
            KycSubmissionRepository kycSubmissionRepository,
            FarmerApplicationRepository farmerApplicationRepository) {
        this.userRepository = userRepository;
        this.kycSubmissionRepository = kycSubmissionRepository;
        this.farmerApplicationRepository = farmerApplicationRepository;
    }

    // Main method — called after login to check which screen to show
    public GateResponse checkGate(Long userId) {

        // Step 1: find the user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Role role = user.getRole();

        // Step 2: ADMIN goes straight through — no gate needed
        if (role == Role.ADMIN) {
            return new GateResponse("PROCEED", null, null);
        }

        // Step 3: INVESTOR — check KYC status
        if (role == Role.INVESTOR) {
            return checkInvestorGate(user);
        }

        // Step 4: FARMER — check application status
        if (role == Role.FARMER) {
            return checkFarmerGate(user);
        }

        // Step 5: AUDITOR — goes straight through (internal staff)
        if (role == Role.AUDITOR) {
            return new GateResponse("PROCEED", null, null);
        }

        // Fallback — unknown role
        return new GateResponse("BLOCKED", null, "Unknown role");
    }

    // ── Investor KYC check ──────────────────────────────────────
    private GateResponse checkInvestorGate(User user) {

        Optional<KycSubmission> latest = kycSubmissionRepository
                .findTopByUserUserIdOrderBySubmittedAtDesc(user.getUserId());

        // No submission found at all
        if (latest.isEmpty()) {
            return new GateResponse("NOT_SUBMITTED", null, null);
        }

        KycSubmission kyc = latest.get();

        return switch (kyc.getStatus()) {
            case PENDING -> new GateResponse("PENDING", null, null);
            case VERIFIED -> new GateResponse("PROCEED", null, null);
            case REJECTED -> new GateResponse("FAILED", null, kyc.getRejectionReason());
            default -> new GateResponse("NOT_SUBMITTED", null, null);
        };
    }

    // ── Farmer application check ────────────────────────────────
    private GateResponse checkFarmerGate(User user) {

        Optional<FarmerApplication> latest = farmerApplicationRepository
                .findTopByUserUserIdOrderBySubmittedAtDesc(user.getUserId());

        // No application found at all
        if (latest.isEmpty()) {
            return new GateResponse("NOT_SUBMITTED", null, null);
        }

        FarmerApplication app = latest.get();

        return switch (app.getStatus()) {
            case PENDING -> new GateResponse("PENDING", null, null);
            case VERIFIED -> new GateResponse("PROCEED", null, null);
            case REJECTED -> new GateResponse(
                    "FAILED",
                    null,
                    app.getRejectionReason());
            default -> new GateResponse("NOT_SUBMITTED", null, null);
        };
    }

    // ── Response object the frontend receives ───────────────────
    // This is a Record — a modern Java feature (Java 16+)
    // It's like a DTO but shorter — no need to write getters manually
    public record GateResponse(
            String status, // PROCEED | PENDING | FAILED | NOT_SUBMITTED
            String redirectTo, // future use — which dashboard URL
            String reason // rejection reason if FAILED
    ) {
    }
}