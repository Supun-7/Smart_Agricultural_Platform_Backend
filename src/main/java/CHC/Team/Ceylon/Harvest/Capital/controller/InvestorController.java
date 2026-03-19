package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.entity.KycSubmission;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import CHC.Team.Ceylon.Harvest.Capital.repository.KycSubmissionRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.security.RequiredRole;
import CHC.Team.Ceylon.Harvest.Capital.service.InvestorDashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/investor")
@CrossOrigin(origins = "*")
public class InvestorController {

        private final UserRepository userRepository;
        private final KycSubmissionRepository kycSubmissionRepository;
        private final JwtUtil jwtUtil;
        private final InvestorDashboardService dashboardService;

        public InvestorController(
                        UserRepository userRepository,
                        KycSubmissionRepository kycSubmissionRepository,
                        JwtUtil jwtUtil,
                        InvestorDashboardService dashboardService) {
                this.userRepository = userRepository;
                this.kycSubmissionRepository = kycSubmissionRepository;
                this.jwtUtil = jwtUtil;
                this.dashboardService = dashboardService;
        }

        private Long extractUserId(String authHeader) {
                String token = authHeader.substring(7);
                return Long.parseLong(jwtUtil.extractUserId(token));
        }

        // ── GET /api/investor/profile ──────────────────────────────────────────
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

        // ── GET /api/investor/dashboard (AC-1) ───────────────────────────────
        @GetMapping("/dashboard")
        @RequiredRole(Role.INVESTOR)
        public ResponseEntity<?> getDashboard(
                        @RequestHeader("Authorization") String authHeader) {

                Long userId = extractUserId(authHeader);
                return ResponseEntity.ok(dashboardService.getDashboard(userId));
        }

        // ── POST /api/investor/kyc ─────────────────────────────────────────────
        @PostMapping("/kyc")
        @RequiredRole(Role.INVESTOR)
        public ResponseEntity<?> submitKyc(
                        @RequestHeader("Authorization") String authHeader,
                        @RequestBody KycRequest request) {

                Long userId = extractUserId(authHeader);
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

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

                KycSubmission kyc = new KycSubmission();
                kyc.setUser(user);
                kyc.setDocumentUrl(request.documentUrl());
                kyc.setStatus(VerificationStatus.PENDING);
                kycSubmissionRepository.save(kyc);

                return ResponseEntity.ok(Map.of(
                                "message", "KYC submitted successfully — under review",
                                "status", "PENDING"));
        }

        // ── GET /api/investor/opportunities ───────────────────────────────────
        @GetMapping("/opportunities")
        @RequiredRole(Role.INVESTOR)
        public ResponseEntity<?> getInvestmentOpportunities() {
                // AC-6: real data from DB — no mock List.of()
                return ResponseEntity.ok(dashboardService.getOpportunities());
        }

        // ── GET /api/investor/portfolio ────────────────────────────────────────
        @GetMapping("/portfolio")
        @RequiredRole(Role.INVESTOR)
        public ResponseEntity<?> getPortfolio(
                        @RequestHeader("Authorization") String authHeader) {
                // AC-6: real data from DB — no mock List.of()
                Long userId = extractUserId(authHeader);
                return ResponseEntity.ok(dashboardService.getPortfolio(userId));
        }

        // ── GET /api/investor/reports ──────────────────────────────────────────
        @GetMapping("/reports")
        @RequiredRole(Role.INVESTOR)
        public ResponseEntity<?> getFinancialReports(
                        @RequestHeader("Authorization") String authHeader) {
                // AC-6: real data from DB — no mock List.of()
                Long userId = extractUserId(authHeader);
                return ResponseEntity.ok(dashboardService.getReports(userId));
        }

        // ── KYC Request DTO ────────────────────────────────────────────────────
        public record KycRequest(String documentUrl) {
        }
}