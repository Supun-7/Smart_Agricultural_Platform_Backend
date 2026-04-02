package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.dto.AdminDashboardResponseDTO;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.exception.AdminDashboardException;
import CHC.Team.Ceylon.Harvest.Capital.security.RequiredRole;
import CHC.Team.Ceylon.Harvest.Capital.service.AdminDashboardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Exposes the admin-only dashboard aggregation endpoint.
 *
 * <p>Security is enforced in two layers:
 * <ol>
 *   <li>{@link RequiredRole} on each method → the {@code RoleInterceptor}
 *       validates the JWT and rejects non-ADMIN callers with 403 before
 *       the method body even runs.</li>
 *   <li>The service layer wraps all DB errors in
 *       {@link AdminDashboardException}, which the
 *       {@code GlobalExceptionHandler} maps to HTTP 500.</li>
 * </ol>
 *
 * <p>Base path is {@code /api/admin} to stay consistent with the existing
 * {@link AdminController} and the interceptor's path pattern {@code /api/**}.
 */
@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    /**
     * Returns a live snapshot of platform-wide statistics for the admin dashboard.
     *
     * <p><b>Authorization:</b> Bearer JWT with role {@code ADMIN} or {@code SYSTEM_ADMIN} required.
     * Non-admin tokens receive {@code 403 Forbidden} from the interceptor
     * before this method is invoked.
     *
     * <p><b>Response structure:</b>
     * <pre>{@code
     * {
     *   "totalFarmers":     <int>,
     *   "totalInvestors":   <int>,
     *   "totalInvestment":  <BigDecimal>,
     *   "farmers":  [ { "id", "name", "email", "role", "status" }, ... ],
     *   "investors": [ { "id", "name", "email", "role", "status" }, ... ]
     * }
     * }</pre>
     *
     * @return {@code 200 OK} with {@link AdminDashboardResponseDTO} on success,
     *         {@code 500 Internal Server Error} with an error message on failure
     */
    @GetMapping("/dashboard")
    @RequiredRole({Role.ADMIN, Role.SYSTEM_ADMIN})
    public ResponseEntity<?> getAdminDashboard() {
        try {
            AdminDashboardResponseDTO dashboardData = adminDashboardService.getDashboardData();
            return ResponseEntity.ok(dashboardData);

        } catch (AdminDashboardException ex) {
            // AdminDashboardException is also handled by GlobalExceptionHandler,
            // but catching it here allows us to log or enrich the response if needed
            // in the future without breaking the global handler contract.
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error",     "Dashboard data could not be loaded",
                            "message",   ex.getMessage(),
                            "timestamp", LocalDateTime.now().toString()
                    ));
        }
    }
}
