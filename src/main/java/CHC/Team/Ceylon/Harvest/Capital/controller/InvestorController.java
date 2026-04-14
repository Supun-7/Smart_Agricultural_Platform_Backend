package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.entity.KycSubmission;
import CHC.Team.Ceylon.Harvest.Capital.entity.Land;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import CHC.Team.Ceylon.Harvest.Capital.repository.KycSubmissionRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.LandRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.exception.BadRequestException;
import CHC.Team.Ceylon.Harvest.Capital.exception.ResourceNotFoundException;
import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.security.RequiredRole;
import CHC.Team.Ceylon.Harvest.Capital.service.InvestmentService;
import CHC.Team.Ceylon.Harvest.Capital.service.InvestorDashboardService;
import CHC.Team.Ceylon.Harvest.Capital.service.InvestorRoiService;
import CHC.Team.Ceylon.Harvest.Capital.service.LandMarketService;
import CHC.Team.Ceylon.Harvest.Capital.dto.investment.InvestRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/investor")
public class InvestorController {

    private final UserRepository userRepository;
    private final KycSubmissionRepository kycSubmissionRepository;
    private final LandRepository landRepository;
    private final JwtUtil jwtUtil;
    private final InvestorDashboardService dashboardService;
    private final InvestmentService investmentService;
    private final InvestorRoiService investorRoiService;
    private final LandMarketService landMarketService;

    public InvestorController(
            UserRepository userRepository,
            KycSubmissionRepository kycSubmissionRepository,
            LandRepository landRepository,
            JwtUtil jwtUtil,
            InvestorDashboardService dashboardService,
            InvestmentService investmentService,
            InvestorRoiService investorRoiService,
            LandMarketService landMarketService) {
        this.userRepository = userRepository;
        this.kycSubmissionRepository = kycSubmissionRepository;
        this.landRepository = landRepository;
        this.jwtUtil = jwtUtil;
        this.dashboardService = dashboardService;
        this.investmentService = investmentService;
        this.investorRoiService = investorRoiService;
        this.landMarketService = landMarketService;
    }

    private Long extractUserId(String authHeader) {
        String token = authHeader.substring(7);
        return Long.parseLong(jwtUtil.extractUserId(token));
    }

    // ── GET /api/investor/profile ─────────────────────────────────────────
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
                "userId",    user.getUserId(),
                "fullName",  user.getFullName(),
                "email",     user.getEmail(),
                "role",      user.getRole().name(),
                "kycStatus", latestKyc.map(k -> k.getStatus().name())
                        .orElse("NOT_SUBMITTED")));
    }

    // ── GET /api/investor/dashboard ───────────────────────────────────────
    @GetMapping("/dashboard")
    @RequiredRole(Role.INVESTOR)
    public ResponseEntity<?> getDashboard(
            @RequestHeader("Authorization") String authHeader) {

        Long userId = extractUserId(authHeader);
        return ResponseEntity.ok(dashboardService.getDashboard(userId));
    }

    // ── POST /api/investor/kyc ────────────────────────────────────────────
    @PostMapping("/kyc")
    @RequiredRole(Role.INVESTOR)
    public ResponseEntity<?> submitKyc(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody KycRequest request) {

        Long userId = extractUserId(authHeader);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Block duplicate active submissions
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

        // Age validation
        if (request.age() == null || request.age() < 18) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "You must be at least 18 years old to submit KYC"));
        }

        // Build and save the full KYC submission — all fields from the form
        KycSubmission kyc = new KycSubmission();
        kyc.setUser(user);
        kyc.setStatus(VerificationStatus.PENDING);

        kyc.setTitle(request.title());
        kyc.setFirstName(request.firstName());
        kyc.setLastName(request.lastName());
        kyc.setAge(request.age());
        kyc.setNationality(request.nationality());
        kyc.setCurrentOccupation(request.currentOccupation());
        kyc.setAddress(request.address());
        kyc.setIdType(request.idType());
        kyc.setIdNumber(request.idNumber());
        kyc.setIdFrontUrl(request.idFrontUrl());
        kyc.setIdBackUrl(request.idBackUrl());
        kyc.setUtilityBillUrl(request.utilityBillUrl());
        kyc.setBankStmtUrl(request.bankStmtUrl());

        kycSubmissionRepository.save(kyc);

        // Update user verification status to PENDING
        user.setVerificationStatus(VerificationStatus.PENDING);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "message", "KYC submitted successfully — under review",
                "status",  "PENDING"));
    }

    // ── GET /api/investor/opportunities ──────────────────────────────────
    @GetMapping("/opportunities")
    @RequiredRole(Role.INVESTOR)
    public ResponseEntity<?> getInvestmentOpportunities() {
        return ResponseEntity.ok(dashboardService.getOpportunities());
    }

    // ── GET /api/investor/portfolio ───────────────────────────────────────
    @GetMapping("/portfolio")
    @RequiredRole(Role.INVESTOR)
    public ResponseEntity<?> getPortfolio(
            @RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        return ResponseEntity.ok(dashboardService.getPortfolio(userId));
    }

    // ── GET /api/investor/reports ─────────────────────────────────────────
    @GetMapping("/reports")
    @RequiredRole(Role.INVESTOR)
    public ResponseEntity<?> getFinancialReports(
            @RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        return ResponseEntity.ok(dashboardService.getReports(userId));
    }


    // ── GET /api/investor/roi/history ─────────────────────────────────────
    @GetMapping("/roi/history")
    @RequiredRole(Role.INVESTOR)
    public ResponseEntity<?> getRoiHistory(
            @RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        return ResponseEntity.ok(investorRoiService.getRoiHistory(userId));
    }

    // ── GET /api/investor/land-market ─────────────────────────────────────
    @GetMapping("/land-market")
    @RequiredRole(Role.INVESTOR)
    public ResponseEntity<?> getLandMarket(
            @RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        return ResponseEntity.ok(landMarketService.getInvestorLandMarket(userId));
    }

    // ── GET /api/investor/contracts ───────────────────────────────────────
    // Returns all investment contracts for the authenticated investor.
    // Each entry includes blockchain tx hash, contract address, and polygonScanUrl.
    @GetMapping("/contracts")
    @RequiredRole(Role.INVESTOR)
    public ResponseEntity<?> getContracts(
            @RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        return ResponseEntity.ok(dashboardService.getContracts(userId));
    }

    // ── GET /api/investor/lands/{landId} ─────────────────────────────────
    @GetMapping("/lands/{landId}")
    @RequiredRole(Role.INVESTOR)
    @Transactional(readOnly = true)
    public ResponseEntity<?> getLandDetail(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long landId) {

        Land land = landRepository.findById(landId)
                .orElseThrow(() -> new ResourceNotFoundException("Land not found: " + landId));

        if (!Boolean.TRUE.equals(land.getIsActive())) {
            throw new BadRequestException("This land is not currently available for investment.");
        }

        Map<String, Object> detail = new HashMap<>();
        detail.put("landId",             land.getLandId());
        detail.put("projectName",        land.getProjectName());
        detail.put("location",           land.getLocation());
        detail.put("totalValue",         land.getTotalValue());
        detail.put("minimumInvestment",  land.getMinimumInvestment());
        detail.put("progressPercentage", land.getProgressPercentage());
        detail.put("sizeAcres",          land.getSizeAcres());
        detail.put("cropType",           land.getCropType());
        detail.put("description",        land.getDescription());
        detail.put("imageUrls",          land.getImageUrls());
        detail.put("farmerName",         land.getFarmerUser() != null
                                             ? land.getFarmerUser().getFullName() : null);
        detail.put("farmerId",           land.getFarmerUser() != null
                                             ? land.getFarmerUser().getUserId() : null);
        detail.put("createdAt",          land.getCreatedAt());
        detail.put("isActive",           land.getIsActive());
        return ResponseEntity.ok(detail);
    }

    // ── POST /api/investor/lands/{landId}/invest ──────────────────────────
    @PostMapping("/lands/{landId}/invest")
    @RequiredRole(Role.INVESTOR)
    public ResponseEntity<?> invest(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long landId,
            @RequestBody InvestRequest request) {

        Long userId = extractUserId(authHeader);
        return ResponseEntity.ok(investmentService.invest(userId, landId, request));
    }

    // ── KYC Request DTO — full form fields ───────────────────────────────
    public record KycRequest(
            String  title,
            String  firstName,
            String  lastName,
            Integer age,
            String  nationality,
            String  currentOccupation,
            String  address,
            String  idType,
            String  idNumber,
            String  idFrontUrl,
            String  idBackUrl,
            String  utilityBillUrl,
            String  bankStmtUrl
    ) {}
}