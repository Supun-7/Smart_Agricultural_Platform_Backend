package CHC.Team.Ceylon.Harvest.Capital.dto.wallet;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTOs for all wallet-related API contracts.
 * Kept in one file for easy discovery; split if this grows.
 */
public final class WalletDtos {

    private WalletDtos() {}

    // ── Inbound ──────────────────────────────────────────────────────────────

    /**
     * AC-1 / AC-4 — body sent by the investor for deposit or withdrawal.
     * amount must be positive (AC-7 enforced in service, not just here).
     */
    public record TransactionRequest(BigDecimal amount) {}

    // ── Outbound ─────────────────────────────────────────────────────────────

    /** AC-2 / AC-6 — returned after a successful deposit or withdrawal. */
    public record TransactionResponse(
            String  transactionType,   // "DEPOSIT" or "WITHDRAWAL"
            BigDecimal amount,
            BigDecimal newBalance,
            String  currency,
            String  gateway,
            String  gatewayReference,
            LocalDateTime createdAt
    ) {}

    /** One row from the ledger history. */
    public record LedgerEntryDto(
            Long   ledgerId,
            String transactionType,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String gateway,
            String gatewayReference,
            LocalDateTime createdAt
    ) {}

    /** AC-8 — full wallet info + history, for QA and normal use. */
    public record WalletResponse(
            Long   walletId,
            BigDecimal balance,
            String currency,
            List<LedgerEntryDto> history
    ) {}
}
