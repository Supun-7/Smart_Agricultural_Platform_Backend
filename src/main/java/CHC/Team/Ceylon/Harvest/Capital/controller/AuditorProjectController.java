package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.dto.auditor.ProjectDecisionRequest;
import CHC.Team.Ceylon.Harvest.Capital.dto.auditor.ProjectDetailResponse;
import CHC.Team.Ceylon.Harvest.Capital.entity.Land;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import CHC.Team.Ceylon.Harvest.Capital.repository.LandRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.security.RequiredRole;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Auditor endpoints for reviewing new project (land) submissions.
 *
 * <pre>
 *   GET  /api/auditor/projects/pending          – list all projects awaiting review
 *   GET  /api/auditor/projects                  – list all projects with their review status
 *   GET  /api/auditor/projects/{landId}         – full detail for one project
 *   PUT  /api/auditor/projects/{landId}/approve – approve (makes project live for investors)
 *   PUT  /api/auditor/projects/{landId}/reject  – reject with a reason
 * </pre>
 */
@RestController
@RequestMapping("/api/auditor/projects")
public class AuditorProjectController {

    private final LandRepository landRepository;
    private final UserRepository userRepository;

    public AuditorProjectController(LandRepository landRepository,
                                    UserRepository userRepository) {
        this.landRepository = landRepository;
        this.userRepository = userRepository;
    }

    // ── List endpoints ────────────────────────────────────────────────────────

    /**
     * Returns every project whose review_status is PENDING.
     * This is the auditor's main work queue for new project submissions.
     */
    @GetMapping("/pending")
    @RequiredRole(Role.AUDITOR)
    @Transactional(readOnly = true)
    public ResponseEntity<?> getPendingProjects() {
        List<Land> pending = landRepository.findByReviewStatusWithFarmer(VerificationStatus.PENDING);
        var list = pending.stream().map(this::toSummary).toList();
        return ResponseEntity.ok(Map.of(
                "count", list.size(),
                "items", list
        ));
    }

    /**
     * Returns all projects regardless of review status.
     * Useful for the auditor to see the full picture (approved, rejected, pending).
     */
    @GetMapping
    @RequiredRole(Role.AUDITOR)
    @Transactional(readOnly = true)
    public ResponseEntity<?> getAllProjects() {
        var list = landRepository.findAllWithFarmer().stream().map(this::toSummary).toList();
        return ResponseEntity.ok(Map.of(
                "count", list.size(),
                "items", list
        ));
    }

    // ── Detail endpoint ───────────────────────────────────────────────────────

    /**
     * Returns the complete detail of a single land/project for the auditor
     * to inspect before making a decision.
     */
    @GetMapping("/{landId}")
    @RequiredRole(Role.AUDITOR)
    @Transactional(readOnly = true)
    public ResponseEntity<ProjectDetailResponse> getProjectDetail(@PathVariable Long landId) {
        Land land = landRepository.findByIdWithFarmer(landId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + landId));
        return ResponseEntity.ok(toDetail(land));
    }

    // ── Decision endpoints ────────────────────────────────────────────────────

    /**
     * Approves a project submission.
     * Sets review_status = VERIFIED and is_active = true so the project
     * becomes visible to investors on the marketplace.
     */
    @PutMapping("/{landId}/approve")
    @RequiredRole(Role.AUDITOR)
    @Transactional
    public ResponseEntity<?> approveProject(
            @PathVariable Long landId,
            HttpServletRequest request) {

        // FIX (CHC-122): guard against null auditorId.
        // userId is placed on the request by RoleInterceptor; if the interceptor
        // is not registered (WebConfig.addInterceptors commented out) this will
        // be null and userRepository.findById(null) crashes.
        Long auditorId = (Long) request.getAttribute("userId");
        if (auditorId == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Unauthorized: missing auditor identity"));
        }

        Land land = landRepository.findByIdWithFarmer(landId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + landId));
        User auditor = userRepository.findById(auditorId)
                .orElseThrow(() -> new RuntimeException("Auditor not found: " + auditorId));

        land.setReviewStatus(VerificationStatus.VERIFIED);
        land.setReviewedAt(LocalDateTime.now());
        land.setReviewedBy(auditor);
        land.setRejectionReason(null);
        land.setIsActive(true);   // project is now publicly visible to investors
        landRepository.save(land);

        return ResponseEntity.ok(Map.of(
                "message",  "Project approved and is now live on the marketplace",
                "landId",   landId,
                "projectName", land.getProjectName()
        ));
    }

    /**
     * Rejects a project submission with a mandatory reason.
     * Sets review_status = REJECTED and keeps is_active = false so the
     * project stays hidden from investors.
     */
    @PutMapping("/{landId}/reject")
    @RequiredRole(Role.AUDITOR)
    @Transactional
    public ResponseEntity<?> rejectProject(
            @PathVariable Long landId,
            @RequestBody ProjectDecisionRequest body,
            HttpServletRequest request) {

        // FIX (CHC-122): guard against null auditorId — see approveProject for detail.
        Long auditorId = (Long) request.getAttribute("userId");
        if (auditorId == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Unauthorized: missing auditor identity"));
        }
        String reason = (body.reason() != null && !body.reason().isBlank())
                ? body.reason() : "No reason provided";

        Land land = landRepository.findByIdWithFarmer(landId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + landId));
        User auditor = userRepository.findById(auditorId)
                .orElseThrow(() -> new RuntimeException("Auditor not found: " + auditorId));

        land.setReviewStatus(VerificationStatus.REJECTED);
        land.setReviewedAt(LocalDateTime.now());
        land.setReviewedBy(auditor);
        land.setRejectionReason(reason);
        land.setIsActive(false);  // keep hidden from investors
        landRepository.save(land);

        return ResponseEntity.ok(Map.of(
                "message",     "Project rejected",
                "landId",      landId,
                "projectName", land.getProjectName(),
                "reason",      reason
        ));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Map<String, Object> toSummary(Land l) {
        String farmerName = "";
        String farmerEmail = "";
        if (l.getFarmerUser() != null) {
            farmerName  = l.getFarmerUser().getFullName();
            farmerEmail = l.getFarmerUser().getEmail();
        }
        return Map.of(
                "landId",        l.getLandId(),
                "projectName",   l.getProjectName(),
                "location",      l.getLocation(),
                "cropType",      l.getCropType()    != null ? l.getCropType()    : "",
                "sizeAcres",     l.getSizeAcres()   != null ? l.getSizeAcres()   : 0,
                "totalValue",    l.getTotalValue(),
                "farmerName",    farmerName,
                "farmerEmail",   farmerEmail,
                "reviewStatus",  l.getReviewStatus() != null ? l.getReviewStatus().name() : "PENDING",
                "createdAt",     l.getCreatedAt().toString()
        );
    }

    private ProjectDetailResponse toDetail(Land l) {
        User farmer = l.getFarmerUser();
        return new ProjectDetailResponse(
                l.getLandId(),
                l.getProjectName(),
                l.getLocation(),
                l.getTotalValue(),
                l.getMinimumInvestment(),
                l.getProgressPercentage(),
                l.getIsActive(),
                l.getSizeAcres(),
                l.getCropType(),
                l.getDescription(),
                l.getImageUrls(),
                l.getCreatedAt(),
                farmer != null ? farmer.getUserId()                         : null,
                farmer != null ? farmer.getFullName()                       : "",
                farmer != null ? farmer.getEmail()                          : "",
                farmer != null ? farmer.getVerificationStatus().name()      : "",
                l.getReviewStatus() != null ? l.getReviewStatus().name()    : "PENDING",
                l.getRejectionReason()
        );
    }
}
