package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.dto.wallet.WalletDtos.*;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.security.RequiredRole;
import CHC.Team.Ceylon.Harvest.Capital.service.WalletService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for investor wallet operations.
 *
 *  GET  /api/investor/wallet          → current balance + ledger history (AC-8)
 *  POST /api/investor/wallet/deposit  → credit amount           (AC-1,2,3,7)
 *  POST /api/investor/wallet/withdraw → debit amount            (AC-4,5,6,7)
 */
@RestController
@RequestMapping("/api/investor/wallet")
public class WalletController {

    private final WalletService walletService;
    private final JwtUtil       jwtUtil;

    public WalletController(WalletService walletService, JwtUtil jwtUtil) {
        this.walletService = walletService;
        this.jwtUtil       = jwtUtil;
    }

    // ── GET /api/investor/wallet ─────────────────────────────────────────────

    @GetMapping
    @RequiredRole(Role.INVESTOR)
    public ResponseEntity<WalletResponse> getWallet(
            @RequestHeader("Authorization") String authHeader) {

        Long userId = extractUserId(authHeader);
        return ResponseEntity.ok(walletService.getWallet(userId));
    }

    // ── POST /api/investor/wallet/deposit ────────────────────────────────────

    @PostMapping("/deposit")
    @RequiredRole(Role.INVESTOR)
    public ResponseEntity<TransactionResponse> deposit(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody TransactionRequest request) {

        Long userId = extractUserId(authHeader);
        TransactionResponse response = walletService.deposit(userId, request.amount());
        return ResponseEntity.ok(response);
    }

    // ── POST /api/investor/wallet/withdraw ───────────────────────────────────

    @PostMapping("/withdraw")
    @RequiredRole(Role.INVESTOR)
    public ResponseEntity<TransactionResponse> withdraw(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody TransactionRequest request) {

        Long userId = extractUserId(authHeader);
        TransactionResponse response = walletService.withdraw(userId, request.amount());
        return ResponseEntity.ok(response);
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private Long extractUserId(String authHeader) {
        String token = authHeader.substring(7);
        return Long.parseLong(jwtUtil.extractUserId(token));
    }
}
