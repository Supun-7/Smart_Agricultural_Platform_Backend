package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.repository.FarmerApplicationRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.KycSubmissionRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.security.RequiredRole;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    // Auditor can VIEW all farms — read only, cannot change anything
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

    @GetMapping("/transactions")
    @RequiredRole(Role.AUDITOR)
    public ResponseEntity<?> getAllTransactions() {
        // TODO: Story 5 — fetch real transactions from transactions table
        return ResponseEntity.ok(Map.of(
            "message",      "All platform transactions",
            "transactions", List.of()
        ));
    }

    @GetMapping("/logs")
    @RequiredRole(Role.AUDITOR)
    public ResponseEntity<?> getAuditLogs() {
        // TODO: Story 6 — implement audit logging table
        return ResponseEntity.ok(Map.of(
            "message", "Audit logs",
            "logs",    List.of()
        ));
    }

    @GetMapping("/reports")
    @RequiredRole(Role.AUDITOR)
    public ResponseEntity<?> generateReports() {
        // TODO: Story 6 — generate platform-wide reports
        var totalFarms     = farmerApplicationRepository.findAll().size();
        var totalKyc       = kycSubmissionRepository.findAll().size();
        var totalUsers     = userRepository.findAll().size();

        return ResponseEntity.ok(Map.of(
            "totalUsers",  totalUsers,
            "totalFarms",  totalFarms,
            "totalKyc",    totalKyc,
            "message",     "Summary report — detailed reports coming in Story 6"
        ));
    }
}