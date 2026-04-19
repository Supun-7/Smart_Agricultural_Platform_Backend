package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.dto.auditor.ComplianceScoreRequest;
import CHC.Team.Ceylon.Harvest.Capital.dto.auditor.ComplianceScoreResponse;
import CHC.Team.Ceylon.Harvest.Capital.dto.auditor.FarmerComplianceListItem;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.security.RequiredRole;
import CHC.Team.Ceylon.Harvest.Capital.service.ComplianceScoringService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints for the farmer compliance scoring feature.
 *
 * Endpoints:
 *   PUT  /api/auditor/farmers/{farmerId}/compliance-score  — AC-3: assign / update score
 *   GET  /api/auditor/farmers/{farmerId}/compliance-score  — AC-4: fetch single farmer score
 *   GET  /api/auditor/farmers/compliance-scores            — AC-4: list all farmers + scores
 *   GET  /api/admin/farmers/compliance-scores              — AC-4: admin view of all scores
 */
@RestController
public class ComplianceScoringController {

    private final ComplianceScoringService scoringService;

    public ComplianceScoringController(ComplianceScoringService scoringService) {
        this.scoringService = scoringService;
    }

    // ── AC-3: Auditor assigns / updates a compliance score ───────────────────

    /**
     * PUT /api/auditor/farmers/{farmerId}/compliance-score
     *
     * AC-3: Only AUDITOR role is permitted.
     * AC-5: Saved immediately; the updated score is returned in the response
     *        so the frontend can reflect the change without a page reload.
     */
    @PutMapping("/api/auditor/farmers/{farmerId}/compliance-score")
    @RequiredRole(Role.AUDITOR)
    public ResponseEntity<?> assignScore(
            @PathVariable Long farmerId,
            @RequestBody @Valid ComplianceScoreRequest request,
            HttpServletRequest httpRequest
    ) {
        Long auditorId = (Long) httpRequest.getAttribute("userId");
        if (auditorId == null) {
            return ResponseEntity.status(401)
                .body(Map.of("error", "Unauthorized: missing auditor identity"));
        }

        try {
            ComplianceScoreResponse response = scoringService.assignScore(farmerId, auditorId, request);
            return ResponseEntity.ok(Map.of(
                "message", "Compliance score saved successfully",
                "score",   response
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
        }
    }

    // ── AC-4: Auditor fetches a single farmer's score ────────────────────────

    /**
     * GET /api/auditor/farmers/{farmerId}/compliance-score
     */
    @GetMapping("/api/auditor/farmers/{farmerId}/compliance-score")
    @RequiredRole(Role.AUDITOR)
    public ResponseEntity<?> getScore(@PathVariable Long farmerId) {
        try {
            ComplianceScoreResponse response = scoringService.getScore(farmerId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
        }
    }

    // ── AC-4: Auditor lists all farmers with compliance scores ───────────────

    /**
     * GET /api/auditor/farmers/compliance-scores
     */
    @GetMapping("/api/auditor/farmers/compliance-scores")
    @RequiredRole(Role.AUDITOR)
    public ResponseEntity<List<FarmerComplianceListItem>> listAllForAuditor() {
        return ResponseEntity.ok(scoringService.listAll());
    }

    // ── AC-4: Admin also has read access to compliance scores ────────────────

    /**
     * GET /api/admin/farmers/compliance-scores
     * AC-4: Admin dashboard can view all compliance scores.
     */
    @GetMapping("/api/admin/farmers/compliance-scores")
    @RequiredRole({Role.ADMIN, Role.SYSTEM_ADMIN})
    public ResponseEntity<List<FarmerComplianceListItem>> listAllForAdmin() {
        return ResponseEntity.ok(scoringService.listAll());
    }
}
