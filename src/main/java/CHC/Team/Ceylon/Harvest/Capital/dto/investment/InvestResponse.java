package CHC.Team.Ceylon.Harvest.Capital.dto.investment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Returned after a successful wallet-funded investment.
 * blockchainTxHash — simulated Ethereum-style transaction hash for the smart contract.
 * contractAddress  — simulated smart contract address on-chain.
 */
public record InvestResponse(
        Long          investmentId,
        Long          landId,
        String        projectName,
        String        location,
        String        cropType,
        BigDecimal    sizeAcres,
        String        farmerName,
        BigDecimal    amountInvested,
        BigDecimal    newWalletBalance,
        String        currency,
        String        ledgerReference,
        String        blockchainTxHash,
        String        contractAddress,
        LocalDateTime investedAt
) {}
