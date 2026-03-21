package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.entity.FarmerApplication;
import CHC.Team.Ceylon.Harvest.Capital.entity.KycSubmission;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import CHC.Team.Ceylon.Harvest.Capital.repository.FarmerApplicationRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.KycSubmissionRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.security.RequiredRole;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auditor")
@CrossOrigin(origins = "*")
public class AuditorController {

    private final UserRepository userRepository;
    private final FarmerApplicationRepository farmerApplicationRepository;
    private final KycSubmissionRepository kycSubmissionRepository;

    public AuditorController(
            UserRepository userRepository,
            FarmerApplicationRepository farmerApplicationRepository,
            KycSubmissionRepository kycSubmissionRepository
    ) {
        this.userRepository = userRepository;
        this.farmerApplicationRepository = farmerApplicationRepository;
        this.kycSubmissionRepository = kycSubmissionRepository;
    }

    // ── AC-1: Dashboard — returns pending KYC + pending farmer applications ──
    @GetMapping("/dashboard")
    @RequiredRole(Role.AUDITOR)
    public ResponseEntity<?> getDashboard() {

        List<KycSubmission> pendingKyc =
            kycSubmissionRepository.findByStatus(VerificationStatus.PENDING);

        List<FarmerApplication> pendingFarmers =
            farmerApplicationRepository.findByStatus(VerificationStatus.PENDING);

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

        return ResponseEntity.ok(Map.of(
            "pendingKyc",     kycList,
            "pendingFarmers", farmerList,
            "kycCount",       kycList.size(),
            "farmerCount",    farmerList.size()
        ));
    }

    // ── KYC Approve ──────────────────────────────────────────────────────────
    @PutMapping("/kyc/{id}/approve")
    @RequiredRole(Role.AUDITOR)
    public ResponseEntity<?> approveKyc(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        Long auditorId = (Long) request.getAttribute("userId");

        KycSubmission kyc = kycSubmissionRepository.findById(id)
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

    // ── KYC Reject ───────────────────────────────────────────────────────────
    @PutMapping("/kyc/{id}/reject")
    @RequiredRole(Role.AUDITOR)
    public ResponseEntity<?> rejectKyc(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            HttpServletRequest request
    ) {
        Long auditorId = (Long) request.getAttribute("userId");
        String reason  = body.getOrDefault("reason", "No reason provided");

        KycSubmission kyc = kycSubmissionRepository.findById(id)
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

    // ── Farmer Approve ───────────────────────────────────────────────────────
    @PutMapping("/farmer/{id}/approve")
    @RequiredRole(Role.AUDITOR)
    public ResponseEntity<?> approveFarmer(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        Long auditorId = (Long) request.getAttribute("userId");

        FarmerApplication farmer = farmerApplicationRepository.findById(id)
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

    // ── Farmer Reject ────────────────────────────────────────────────────────
    @PutMapping("/farmer/{id}/reject")
    @RequiredRole(Role.AUDITOR)
    public ResponseEntity<?> rejectFarmer(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            HttpServletRequest request
    ) {
        Long auditorId = (Long) request.getAttribute("userId");
        String reason  = body.getOrDefault("reason", "No reason provided");

        FarmerApplication farmer = farmerApplicationRepository.findById(id)
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

    // ── Existing read-only endpoints ─────────────────────────────────────────
    @GetMapping("/farms")
    @RequiredRole(Role.AUDITOR)
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
    public ResponseEntity<?> generateReports() {
        return ResponseEntity.ok(Map.of(
            "totalUsers", userRepository.findAll().size(),
            "totalFarms", farmerApplicationRepository.findAll().size(),
            "totalKyc",   kycSubmissionRepository.findAll().size(),
            "message",    "Summary report — detailed reports coming in Story 6"
        ));
    }
}