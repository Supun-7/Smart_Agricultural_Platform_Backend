package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.dto.wallet.WalletDtos.TransactionResponse;
import CHC.Team.Ceylon.Harvest.Capital.dto.wallet.WalletDtos.WalletResponse;

import java.math.BigDecimal;

/**
 * Business operations for investor wallet management.
 *
 * Each method maps to one or more Acceptance Criteria:
 *   deposit   → AC-1, AC-2, AC-3, AC-7
 *   withdraw  → AC-4, AC-5, AC-6, AC-7
 *   getWallet → AC-8 (QA / balance check)
 */
public interface WalletService {

    /**
     * AC-1 / AC-2 / AC-3 / AC-7
     * Credit {@code amount} to the investor's wallet via the configured gateway.
     * Throws {@link CHC.Team.Ceylon.Harvest.Capital.exception.BadRequestException}
     * if amount ≤ 0.
     */
    TransactionResponse deposit(Long userId, BigDecimal amount);

    /**
     * AC-4 / AC-5 / AC-6 / AC-7
     * Debit {@code amount} from the investor's wallet.
     * Throws {@link CHC.Team.Ceylon.Harvest.Capital.exception.BadRequestException}
     * if amount ≤ 0 or amount > current balance.
     */
    TransactionResponse withdraw(Long userId, BigDecimal amount);

    /**
     * AC-8
     * Returns current balance plus full transaction history for the investor.
     */
    WalletResponse getWallet(Long userId);
}
