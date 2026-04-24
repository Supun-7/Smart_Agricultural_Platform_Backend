package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.dto.MilestoneSummaryResponse;
import CHC.Team.Ceylon.Harvest.Capital.entity.FarmerApplication;
import CHC.Team.Ceylon.Harvest.Capital.entity.KycSubmission;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import CHC.Team.Ceylon.Harvest.Capital.repository.FarmerApplicationRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.InvestmentRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.KycSubmissionRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.LandRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.security.RequiredRole;
import CHC.Team.Ceylon.Harvest.Capital.service.MilestoneService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auditor")
public class AuditorController {

    private final UserRepository userRepository;
    private final FarmerApplicationRepository farmerApplicationRepository;
    private final KycSubmissionRepository kycSubmissionRepository;
    private final MilestoneService milestoneService;
    private final InvestmentRepository investmentRepository;
    private final LandRepository landRepository;

    public AuditorController(
            UserRepository userRepository,
            FarmerApplicationRepository farmerApplicationRepository,
            KycSubmissionRepository kycSubmissionRepository,
            MilestoneService milestoneService,
            InvestmentRepository investmentRepository,
            LandRepository landRepository
    ) {
        this.userRepository = userRepository;
        this.farmerApplicationRepository = farmerApplicationRepository;
        this.kycSubmissionRepository = kycSubmissionRepository;
        this.milestoneService = milestoneService;
        this.investmentRepository = investmentRepository;
        this.landRepository = landRepository;
    }

    @GetMapping("/dashboard")
    @RequiredRole(Role.AUDITOR)
    @Transactional(readOnly = true)
    public ResponseEntity<?> getDashboard() {

        // join fetch k.user so getEmail() never hits a closed session
        List<KycSubmission> pendingKyc =
            kycSubmissionRepository.findByStatusWithUser(VerificationStatus.PENDING);

        // join fetch f.user so getEmail() never hits a closed session
        List<FarmerApplication> pendingFarmers =
            farmerApplicationRepository.findByStatusWithUser(VerificationStatus.PENDING);

        List<MilestoneSummaryResponse> pendingMilestones =
            milestoneService.getPendingMilestones();

        long pendingProjectCount = landRepository
            .findByReviewStatusWithFarmer(VerificationStatus.PENDING).size();

        var kycList = pendingKyc.stream().map(k -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id",             k.getId());
            map.put("fullName",       k.getFirstName() + " " + k.getLastName());
            map.put("email",          k.getUser().getEmail());
            map.put("nationality",    k.getNationality()    != null ? k.getNationality()    : "");
            map.put("idType",         k.getIdType()         != null ? k.getIdType()         : "");
            map.put("idNumber",       k.getIdNumber()       != null ? k.getIdNumber()       : "");
            map.put("address",        k.getAddress()        != null ? k.getAddress()        : "");
            map.put("idFrontUrl",     k.getIdFrontUrl()     != null ? k.getIdFrontUrl()     : "");
            map.put("idBackUrl",      k.getIdBackUrl()      != null ? k.getIdBackUrl()      : "");
            map.put("utilityBillUrl", k.getUtilityBillUrl() != null ? k.getUtilityBillUrl() : "");
            map.put("bankStmtUrl",    k.getBankStmtUrl()    != null ? k.getBankStmtUrl()    : "");
            map.put("submittedAt",    k.getSubmittedAt().toString());
            map.put("status",         k.getStatus().name());
            return map;
        }).toList();

        var farmerList = pendingFarmers.stream().map(f -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id",            f.getId());
            map.put("farmerName",    f.getFarmerName()    != null ? f.getFarmerName()    : "");
            map.put("surname",       f.getSurname()       != null ? f.getSurname()       : "");
            map.put("email",         f.getUser().getEmail());
            map.put("nicNumber",     f.getNicNumber()     != null ? f.getNicNumber()     : "");
            map.put("farmLocation",  f.getFarmLocation()  != null ? f.getFarmLocation()  : "");
            map.put("landSizeAcres", f.getLandSizeAcres() != null ? f.getLandSizeAcres() : 0);
            map.put("cropTypes",     f.getCropTypes()     != null ? f.getCropTypes()     : "");
            map.put("nicFrontUrl",   f.getNicFrontUrl()   != null ? f.getNicFrontUrl()   : "");
            map.put("nicBackUrl",    f.getNicBackUrl()    != null ? f.getNicBackUrl()    : "");
            map.put("landPhotoUrls", f.getLandPhotoUrls() != null ? f.getLandPhotoUrls() : "");
            map.put("submittedAt",   f.getSubmittedAt().toString());
            map.put("status",        f.getStatus().name());
            return map;
        }).toList();

        java.math.BigDecimal totalInvested = investmentRepository.sumTotalInvestmentPlatformWide();
        long totalInvestors = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.INVESTOR).count();
        Map<String, Object> investorActivity = new HashMap<>();
        investorActivity.put("totalInvestors",        totalInvestors);
        investorActivity.put("pendingKycCount",        pendingKyc.size());
        investorActivity.put("totalInvestedPlatform", totalInvested);

        return ResponseEntity.ok(Map.of(
            "pendingKyc",        kycList,
            "pendingFarmers",    farmerList,
            "pendingMilestones", pendingMilestones,
            "investorActivity",  investorActivity,
            "kycCount",          kycList.size(),
            "farmerCount",       farmerList.size(),
            "milestoneCount",    pendingMilestones.size(),
            "projectCount",      pendingProjectCount
        ));
    }

    @PutMapping("/kyc/{id}/approve")
    @RequiredRole(Role.AUDITOR)
    @Transactional
    public ResponseEntity<?> approveKyc(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        // FIX (CHC-122): guard against null auditorId (set by RoleInterceptor).
        // This is a defence-in-depth check; the real fix is re-enabling the
        // interceptor in WebConfig.  Without the interceptor, userId is never
        // placed on the request and findById(null) crashes with
        // InvalidDataAccessApiUsageException.
        Long auditorId = (Long) request.getAttribute("userId");
        if (auditorId == null) {
            return ResponseEntity.status(401)
                .body(Map.of("error", "Unauthorized: missing auditor identity"));
        }

        KycSubmission kyc = kycSubmissionRepository.findByIdWithUser(id)
            .orElseThrow(() -> new RuntimeException("KYC not found: " + id));

        User auditor = userRepository.findById(auditorId)
            .orElseThrow(() -> new RuntimeException("Auditor not found: " + auditorId));

        kyc.setStatus(VerificationStatus.VERIFIED);
        kyc.setReviewedAt(LocalDateTime.now());
        kyc.setReviewedBy(auditor);
        kyc.setRejectionReason(null);
        kycSubmissionRepository.save(kyc);

        User investor = kyc.getUser();
        investor.setVerificationStatus(VerificationStatus.VERIFIED);
        userRepository.save(investor);

        return ResponseEntity.ok(Map.of(
            "message", "KYC approved successfully",
            "id",      id
        ));
    }

    @PutMapping("/kyc/{id}/reject")
    @RequiredRole(Role.AUDITOR)
    @Transactional
    public ResponseEntity<?> rejectKyc(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            HttpServletRequest request
    ) {
        // FIX (CHC-122): guard against null auditorId — see approveKyc for detail.
        Long auditorId = (Long) request.getAttribute("userId");
        if (auditorId == null) {
            return ResponseEntity.status(401)
                .body(Map.of("error", "Unauthorized: missing auditor identity"));
        }
        String reason  = body.getOrDefault("reason", "No reason provided");

        KycSubmission kyc = kycSubmissionRepository.findByIdWithUser(id)
            .orElseThrow(() -> new RuntimeException("KYC not found: " + id));

        User auditor = userRepository.findById(auditorId)
            .orElseThrow(() -> new RuntimeException("Auditor not found: " + auditorId));

        kyc.setStatus(VerificationStatus.REJECTED);
        kyc.setReviewedAt(LocalDateTime.now());
        kyc.setReviewedBy(auditor);
        kyc.setRejectionReason(reason);
        kycSubmissionRepository.save(kyc);

        User investor = kyc.getUser();
        investor.setVerificationStatus(VerificationStatus.REJECTED);
        userRepository.save(investor);

        return ResponseEntity.ok(Map.of(
            "message", "KYC rejected",
            "id",      id,
            "reason",  reason
        ));
    }

    @PutMapping("/farmer/{id}/approve")
    @RequiredRole(Role.AUDITOR)
    @Transactional
    public ResponseEntity<?> approveFarmer(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        // FIX (CHC-122): guard against null auditorId — see approveKyc for detail.
        // This was the specific endpoint that produced the reported stack trace.
        Long auditorId = (Long) request.getAttribute("userId");
        if (auditorId == null) {
            return ResponseEntity.status(401)
                .body(Map.of("error", "Unauthorized: missing auditor identity"));
        }

        FarmerApplication farmer = farmerApplicationRepository.findByIdWithUser(id)
            .orElseThrow(() -> new RuntimeException("Farmer application not found: " + id));

        User auditor = userRepository.findById(auditorId)
            .orElseThrow(() -> new RuntimeException("Auditor not found: " + auditorId));

        farmer.setStatus(VerificationStatus.VERIFIED);
        farmer.setReviewedAt(LocalDateTime.now());
        farmer.setReviewedBy(auditor);
        farmer.setRejectionReason(null);
        farmerApplicationRepository.save(farmer);

        User farmerUser = farmer.getUser();
        farmerUser.setVerificationStatus(VerificationStatus.VERIFIED);
        userRepository.save(farmerUser);

        return ResponseEntity.ok(Map.of(
            "message", "Farmer application approved successfully",
            "id",      id
        ));
    }

    @PutMapping("/farmer/{id}/reject")
    @RequiredRole(Role.AUDITOR)
    @Transactional
    public ResponseEntity<?> rejectFarmer(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            HttpServletRequest request
    ) {
        // FIX (CHC-122): guard against null auditorId — see approveKyc for detail.
        Long auditorId = (Long) request.getAttribute("userId");
        if (auditorId == null) {
            return ResponseEntity.status(401)
                .body(Map.of("error", "Unauthorized: missing auditor identity"));
        }
        String reason  = body.getOrDefault("reason", "No reason provided");

        FarmerApplication farmer = farmerApplicationRepository.findByIdWithUser(id)
            .orElseThrow(() -> new RuntimeException("Farmer application not found: " + id));

        User auditor = userRepository.findById(auditorId)
            .orElseThrow(() -> new RuntimeException("Auditor not found: " + auditorId));

        farmer.setStatus(VerificationStatus.REJECTED);
        farmer.setReviewedAt(LocalDateTime.now());
        farmer.setReviewedBy(auditor);
        farmer.setRejectionReason(reason);
        farmerApplicationRepository.save(farmer);

        User farmerUser = farmer.getUser();
        farmerUser.setVerificationStatus(VerificationStatus.REJECTED);
        userRepository.save(farmerUser);

        return ResponseEntity.ok(Map.of(
            "message", "Farmer application rejected",
            "id",      id,
            "reason",  reason
        ));
    }

    @GetMapping("/farms")
    @RequiredRole(Role.AUDITOR)
    @Transactional(readOnly = true)
    public ResponseEntity<?> getAllFarms() {
        var farms = farmerApplicationRepository.findAll();
        return ResponseEntity.ok(Map.of(
            "total", farms.size(),
            "farms", farms.stream().map(f -> Map.of(
                "id",           f.getId(),
                "farmLocation", f.getFarmLocation(),
                "cropTypes",    f.getCropTypes() != null ? f.getCropTypes() : "",
                "status",       f.getStatus().name()
            )).toList()
        ));
    }

    @GetMapping("/users")
    @RequiredRole(Role.AUDITOR)
    @Transactional(readOnly = true)
    public ResponseEntity<?> getAllUsers() {
        var users = userRepository.findAll();
        return ResponseEntity.ok(Map.of(
            "total", users.size(),
            "users", users.stream().map(u -> Map.of(
                "userId",   u.getUserId(),
                "fullName", u.getFullName(),
                "email",    u.getEmail(),
                "role",     u.getRole().name(),
                "status",   u.getVerificationStatus().name()
            )).toList()
        ));
    }

    @GetMapping("/reports")
    @RequiredRole(Role.AUDITOR)
    @Transactional(readOnly = true)
    public ResponseEntity<?> generateReports() {
        return ResponseEntity.ok(Map.of(
            "totalUsers", userRepository.findAll().size(),
            "totalFarms", farmerApplicationRepository.findAll().size(),
            "totalKyc",   kycSubmissionRepository.findAll().size(),
            "message",    "Summary report — detailed reports coming in Story 6"
        ));
    }
}
