package CHC.Team.Ceylon.Harvest.Capital.dto.investment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Returned after a successful wallet-funded investment.
 */
public record InvestResponse(
        Long          investmentId,
        Long          landId,
        String        projectName,
        BigDecimal    amountInvested,
        BigDecimal    newWalletBalance,
        String        currency,
        String        ledgerReference,
        LocalDateTime investedAt
) {}
