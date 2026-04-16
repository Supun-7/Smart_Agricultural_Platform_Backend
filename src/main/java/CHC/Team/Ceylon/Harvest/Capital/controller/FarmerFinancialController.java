package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.dto.farmer.FarmerFinancialReportResponse;
import CHC.Team.Ceylon.Harvest.Capital.dto.farmer.YieldRecordRequest;
import CHC.Team.Ceylon.Harvest.Capital.dto.farmer.YieldRecordResponse;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.security.RequiredRole;
import CHC.Team.Ceylon.Harvest.Capital.service.FarmerFinancialService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for the Farmer Financial Report and Yield Tracking feature.
 *
 * <pre>
 *   GET  /api/farmer/financial-report   → AC-1, AC-2, AC-5
 *   POST /api/farmer/yield              → AC-3, AC-4
 *   GET  /api/farmer/yield              → AC-4
 * </pre>
 *
 * All endpoints are protected by {@code @RequiredRole(Role.FARMER)} —
 * the {@link CHC.Team.Ceylon.Harvest.Capital.security.RoleInterceptor}
 * enforces this before the method body runs.
 */
@RestController
@RequestMapping({"/api/farmer", "/farmer"})
public class FarmerFinancialController {

    private final JwtUtil               jwtUtil;
    private final FarmerFinancialService farmerFinancialService;

    public FarmerFinancialController(
            JwtUtil               jwtUtil,
            FarmerFinancialService farmerFinancialService) {
        this.jwtUtil                = jwtUtil;
        this.farmerFinancialService = farmerFinancialService;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Long extractUserId(String authHeader) {
        String token = authHeader.substring(7);        // strip "Bearer "
        return Long.parseLong(jwtUtil.extractUserId(token));
    }

    // ── AC-1 / AC-2 / AC-5  Financial Report ─────────────────────────────────

    /**
     * GET /api/farmer/financial-report
     *
     * <p>Returns the farmer's complete financial report:
     * <ul>
     *   <li>AC-1 — financial report section is present in the response</li>
     *   <li>AC-2 — total funding received per project and as a platform total</li>
     *   <li>AC-5 — funding values sourced from Investment and Ledger tables</li>
     * </ul>
     *
     * @param authHeader  Bearer token from the Authorization header
     * @return 200 OK with {@link FarmerFinancialReportResponse}
     */
    @GetMapping("/financial-report")
    @RequiredRole(Role.FARMER)
    public ResponseEntity<FarmerFinancialReportResponse> getFinancialReport(
            @RequestHeader("Authorization") String authHeader) {

        Long userId = extractUserId(authHeader);
        FarmerFinancialReportResponse report = farmerFinancialService.getFinancialReport(userId);
        return ResponseEntity.ok(report);
    }

    // ── AC-3 / AC-4  Yield Tracking ───────────────────────────────────────────

    /**
     * POST /api/farmer/yield
     *
     * <p>Accepts a harvest yield submission from the farmer.
     * <ul>
     *   <li>AC-3 — form fields: yieldAmountKg, harvestDate, optional landId &amp; notes</li>
     *   <li>AC-4 — record is persisted and returned immediately</li>
     * </ul>
     *
     * @param authHeader  Bearer token from the Authorization header
     * @param request     validated yield payload
     * @return 201 Created with the saved {@link YieldRecordResponse}
     */
    @PostMapping("/yield")
    @RequiredRole(Role.FARMER)
    public ResponseEntity<YieldRecordResponse> submitYield(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody YieldRecordRequest request) {

        Long userId = extractUserId(authHeader);
        YieldRecordResponse saved = farmerFinancialService.submitYield(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * GET /api/farmer/yield
     *
     * <p>Returns the full yield submission history for the authenticated farmer,
     * ordered by harvest date descending (most recent first).
     * <ul>
     *   <li>AC-4 — history list displayed from database</li>
     * </ul>
     *
     * @param authHeader  Bearer token from the Authorization header
     * @return 200 OK with list of {@link YieldRecordResponse}
     */
    @GetMapping("/yield")
    @RequiredRole(Role.FARMER)
    public ResponseEntity<List<YieldRecordResponse>> getYieldHistory(
            @RequestHeader("Authorization") String authHeader) {

        Long userId = extractUserId(authHeader);
        List<YieldRecordResponse> history = farmerFinancialService.getYieldHistory(userId);
        return ResponseEntity.ok(history);
    }
}
