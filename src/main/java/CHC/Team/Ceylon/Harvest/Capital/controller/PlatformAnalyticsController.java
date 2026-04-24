package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.dto.PlatformAnalyticsDTO;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.exception.AdminDashboardException;
import CHC.Team.Ceylon.Harvest.Capital.security.RequiredRole;
import CHC.Team.Ceylon.Harvest.Capital.service.PlatformAnalyticsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Exposes the platform-wide analytics endpoint for the admin dashboard.
 *
 * <p><b>AC-1</b> – An analytics section is visible on the admin dashboard:
 * this endpoint ({@code GET /api/admin/analytics}) is the data source
 * that powers that section.
 *
 * <p>Security mirrors {@link AdminDashboardController}:
 * <ul>
 *   <li>{@link RequiredRole} restricts access to {@code ADMIN} and {@code SYSTEM_ADMIN}.</li>
 *   <li>{@link AdminDashboardException} is mapped to HTTP 500 in the global handler.</li>
 * </ul>
 *
 * <p>Base path {@code /api/admin} keeps this controller consistent with
 * existing admin endpoints and the interceptor's path pattern {@code /api/**}.
 */
@RestController
@RequestMapping("/api/admin")
public class PlatformAnalyticsController {

    private final PlatformAnalyticsService platformAnalyticsService;

    public PlatformAnalyticsController(PlatformAnalyticsService platformAnalyticsService) {
        this.platformAnalyticsService = platformAnalyticsService;
    }

    /**
     * Returns a live analytics snapshot for the admin dashboard.
     *
     * <p><b>Authorization:</b> Bearer JWT with role {@code ADMIN} or
     * {@code SYSTEM_ADMIN} required. Non-admin tokens receive
     * {@code 403 Forbidden} from the {@code RoleInterceptor} before
     * this method is invoked.
     *
     * <p><b>Response structure (AC-2 → AC-5):</b>
     * <pre>{@code
     * {
     *   "totalInvestment": <BigDecimal>,           // AC-2
     *   "activeUsersByRole": {                     // AC-3
     *     "farmers":   <long>,
     *     "investors": <long>,
     *     "auditors":  <long>
     *   },
     *   "projectStats": {                          // AC-4
     *     "active":    <long>,
     *     "funded":    <long>,
     *     "completed": <long>
     *   },
     *   "investmentDistribution": [                // AC-5 chart data
     *     { "landId": <long>, "projectName": <string>, "totalInvested": <BigDecimal> },
     *     ...
     *   ]
     * }
     * }</pre>
     *
     * @return {@code 200 OK} with {@link PlatformAnalyticsDTO} on success,
     *         {@code 500 Internal Server Error} with an error envelope on failure
     */
    @GetMapping("/analytics")
    @RequiredRole({Role.ADMIN, Role.SYSTEM_ADMIN})
    public ResponseEntity<?> getPlatformAnalytics() {
        try {
            PlatformAnalyticsDTO analytics = platformAnalyticsService.getAnalytics();
            return ResponseEntity.ok(analytics);

        } catch (AdminDashboardException ex) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error",     "Analytics data could not be loaded",
                            "message",   ex.getMessage(),
                            "timestamp", LocalDateTime.now().toString()
                    ));
        }
    }
}
