package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.security.RequiredRole;
import CHC.Team.Ceylon.Harvest.Capital.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Exposes the auditor's read-only audit history (CHC-207-3, CHC-207-4).
 *
 * <pre>
 *   GET /api/auditor/history   — returns the calling auditor's full action log
 * </pre>
 *
 * No POST / PUT / DELETE endpoints are provided (AC-5).
 */
@RestController
@RequestMapping("/api/auditor/history")
@CrossOrigin(origins = "*")
public class AuditorHistoryController {

    private final AuditLogService auditLogService;

    public AuditorHistoryController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * Returns the complete audit history for the authenticated auditor.
     * Entries are ordered most-recent first (AC-4).
     *
     * <p>Response shape:
     * <pre>
     * {
     *   "count": 3,
     *   "items": [
     *     {
     *       "auditLogId": 7,
     *       "actionType": "APPROVED",
     *       "milestoneId": 12,
     *       "farmerName": "Kamal Perera",
     *       "auditorId": 5,
     *       "actionedAt": "2025-06-01T09:15:32"
     *     },
     *     ...
     *   ]
     * }
     * </pre>
     */
    @GetMapping
    @RequiredRole(Role.AUDITOR)
    public ResponseEntity<?> getAuditHistory(HttpServletRequest request) {
        Long auditorId = (Long) request.getAttribute("userId");
        List<?> items = auditLogService.getHistoryForAuditor(auditorId);
        return ResponseEntity.ok(Map.of(
                "count", items.size(),
                "items", items
        ));
    }
}
