package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.entity.KycSubmission;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import CHC.Team.Ceylon.Harvest.Capital.repository.KycSubmissionRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.security.RequiredRole;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/investor")
@CrossOrigin(origins = "*")
public class InvestorController {

        private final UserRepository userRepository;
        private final KycSubmissionRepository kycSubmissionRepository;
        private final JwtUtil jwtUtil;

        public InvestorController(
                        UserRepository userRepository,
                        KycSubmissionRepository kycSubmissionRepository,
                        JwtUtil jwtUtil) {
                this.userRepository = userRepository;
                this.kycSubmissionRepository = kycSubmissionRepository;
                this.jwtUtil = jwtUtil;
        }

        private Long extractUserId(String authHeader) {
                String token = authHeader.substring(7);
                return Long.parseLong(jwtUtil.extractUserId(token));
        }

        @GetMapping("/profile")
        @RequiredRole(Role.INVESTOR)
        public ResponseEntity<?> getInvestorProfile(
                        @RequestHeader("Authorization") String authHeader) {
                Long userId = extractUserId(authHeader);
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                var latestKyc = kycSubmissionRepository
                                .findTopByUserUserIdOrderBySubmittedAtDesc(userId);

                return ResponseEntity.ok(Map.of(
                                "userId", user.getUserId(),
                                "fullName", user.getFullName(),
                                "email", user.getEmail(),
                                "role", user.getRole().name(),
                                "kycStatus", latestKyc.map(k -> k.getStatus().name())
                                                .orElse("NOT_SUBMITTED")));
        }

        // ── Submit KYC — now accepts full form data ───────────────
        @PostMapping("/kyc")
        @RequiredRole(Role.INVESTOR)
        public ResponseEntity<?> submitKyc(
                        @RequestHeader("Authorization") String authHeader,
                        @RequestBody KycRequest request) {
                Long userId = extractUserId(authHeader);
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                // Block if already pending or approved
                var existing = kycSubmissionRepository
                                .findTopByUserUserIdOrderBySubmittedAtDesc(userId);

                if (existing.isPresent()) {
                        VerificationStatus currentStatus = existing.get().getStatus();
                        if (currentStatus == VerificationStatus.PENDING ||
                                        currentStatus == VerificationStatus.VERIFIED) {
                                return ResponseEntity.badRequest().body(Map.of(
                                                "error", "You already have an active KYC submission"));
                        }
                }

                // Build KYC submission from full form data
                KycSubmission kyc = new KycSubmission();
                kyc.setUser(user);

                // Personal details
                kyc.setTitle(request.title());
                kyc.setFirstName(request.firstName());
                kyc.setLastName(request.lastName());

                kyc.setAge(request.age());
                if (request.age() != null && request.age() < 18) {
                        return ResponseEntity.badRequest().body(Map.of(
                                        "error", "You must be at least 18 years old to register as an investor"));
                }

                kyc.setNationality(request.nationality());
                kyc.setCurrentOccupation(request.currentOccupation());
                kyc.setAddress(request.address());
                kyc.setIdType(request.idType());
                kyc.setIdNumber(request.idNumber());

                // Document URLs from Supabase Storage
                kyc.setIdFrontUrl(request.idFrontUrl());
                kyc.setIdBackUrl(request.idBackUrl());
                kyc.setUtilityBillUrl(request.utilityBillUrl());
                kyc.setBankStmtUrl(request.bankStmtUrl());

                kyc.setStatus(VerificationStatus.PENDING);

                kycSubmissionRepository.save(kyc);

                return ResponseEntity.ok(Map.of(
                                "message", "KYC submitted successfully — under review",
                                "status", "PENDING"));
        }

        @GetMapping("/opportunities")
        @RequiredRole(Role.INVESTOR)
        public ResponseEntity<?> getInvestmentOpportunities() {
                return ResponseEntity.ok(Map.of(
                                "message", "Investment opportunities",
                                "opportunities", List.of()));
        }

        @GetMapping("/portfolio")
        @RequiredRole(Role.INVESTOR)
        public ResponseEntity<?> getPortfolio(
                        @RequestHeader("Authorization") String authHeader) {
                Long userId = extractUserId(authHeader);
                return ResponseEntity.ok(Map.of(
                                "message", "Portfolio for investor " + userId,
                                "investments", List.of()));
        }

        @GetMapping("/reports")
        @RequiredRole(Role.INVESTOR)
        public ResponseEntity<?> getFinancialReports(
                        @RequestHeader("Authorization") String authHeader) {
                Long userId = extractUserId(authHeader);
                return ResponseEntity.ok(Map.of(
                                "message", "Financial reports for investor " + userId,
                                "reports", List.of()));
        }

        // ── Full KYC request DTO ──────────────────────────────────
        public record KycRequest(
                        // Personal details
                        String title,
                        String firstName,
                        String lastName,
                        Integer age,
                        String nationality,
                        String currentOccupation,
                        String address,
                        String idType,
                        String idNumber,

                        // Document URLs — uploaded to Supabase Storage by frontend
                        String idFrontUrl,
                        String idBackUrl,
                        String utilityBillUrl,
                        String bankStmtUrl) {
        }
}