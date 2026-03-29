package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.dto.MilestoneDecisionRequest;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.security.RequiredRole;
import CHC.Team.Ceylon.Harvest.Capital.service.MilestoneService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auditor/milestones")
@CrossOrigin(origins = "*")
public class MilestoneAuditorController {

    private final MilestoneService milestoneService;

    public MilestoneAuditorController(MilestoneService milestoneService) {
        this.milestoneService = milestoneService;
    }

    @GetMapping("/pending")
    @RequiredRole(Role.AUDITOR)
    public ResponseEntity<?> getPendingMilestones() {
        var items = milestoneService.getPendingMilestones();
        return ResponseEntity.ok(Map.of(
                "items", items,
                "count", items.size()
        ));
    }

    @GetMapping("/{milestoneId}")
    @RequiredRole(Role.AUDITOR)
    public ResponseEntity<?> getMilestoneDetail(@PathVariable Long milestoneId) {
        return ResponseEntity.ok(milestoneService.getMilestoneDetail(milestoneId));
    }

    @PutMapping("/{milestoneId}/approve")
    @RequiredRole(Role.AUDITOR)
    public ResponseEntity<?> approveMilestone(
            @PathVariable Long milestoneId,
            HttpServletRequest request
    ) {
        Long auditorId = (Long) request.getAttribute("userId");
        var milestone = milestoneService.approveMilestone(milestoneId, auditorId);
        return ResponseEntity.ok(Map.of(
                "message", "Milestone approved successfully",
                "milestone", milestone
        ));
    }

    @PutMapping("/{milestoneId}/reject")
    @RequiredRole(Role.AUDITOR)
    public ResponseEntity<?> rejectMilestone(
            @PathVariable Long milestoneId,
            @Valid @RequestBody MilestoneDecisionRequest requestBody,
            HttpServletRequest request
    ) {
        Long auditorId = (Long) request.getAttribute("userId");
        var milestone = milestoneService.rejectMilestone(milestoneId, auditorId, requestBody.reason());
        return ResponseEntity.ok(Map.of(
                "message", "Milestone rejected successfully",
                "milestone", milestone
        ));
    }
}
