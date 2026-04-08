package CHC.Team.Ceylon.Harvest.Capital.service.blockchain;

import java.math.BigDecimal;

/**
 * Abstraction layer for all on-chain operations.
 *
 * The system (CHC) always pays the gas using its own funded Polygon Amoy wallet.
 * Neither investors nor farmers need a crypto wallet — the platform manages everything.
 *
 * Implementations:
 *  - MockBlockchainService          — used when spring.profiles.active is NOT "production"
 *  - PolygonAmoyBlockchainService   — used when spring.profiles.active=production
 */
public interface BlockchainService {

    /** Human-readable network name stored in investment records. e.g. "MOCK" or "POLYGON_AMOY" */
    String networkName();

    /**
     * Records an investment agreement on-chain.
     * Gas is paid by the CHC system wallet — no wallet required from investor or farmer.
     *
     * @param investorId   CHC platform investor user ID (used as identifier in the on-chain data)
     * @param farmerId     CHC platform farmer user ID
     * @param landId       CHC platform land ID
     * @param amountLkr    investment amount in LKR
     * @return result containing txHash and contractAddress (both = txHash on data-tx pattern)
     */
    ContractResult createInvestmentContract(
            Long       investorId,
            Long       farmerId,
            Long       landId,
            BigDecimal amountLkr
    );

    /**
     * Records a milestone approval event on-chain, linked back to the original investment tx.
     *
     * @param contractAddress  the txHash of the original investment (serves as the contract ref)
     * @param milestoneId      CHC platform milestone ID
     * @param progressPct      milestone completion percentage (0-100)
     */
    ContractResult recordMilestoneApproval(
            String contractAddress,
            Long   milestoneId,
            int    progressPct
    );

    // ── Result value object ──────────────────────────────────────────────────

    record ContractResult(
            boolean success,
            String  txHash,
            String  contractAddress,
            String  errorMessage
    ) {
        public static ContractResult deployed(String txHash, String contractAddress) {
            return new ContractResult(true, txHash, contractAddress, null);
        }

        public static ContractResult ok(String txHash) {
            return new ContractResult(true, txHash, null, null);
        }

        public static ContractResult fail(String errorMessage) {
            return new ContractResult(false, null, null, errorMessage);
        }
    }
}
