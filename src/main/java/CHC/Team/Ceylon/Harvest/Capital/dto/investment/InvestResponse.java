package CHC.Team.Ceylon.Harvest.Capital.dto.investment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Returned after a successful investment.
 *
 * blockchainTxHash  — real Polygon Amoy tx hash (or MOCK hash in dev mode).
 * contractAddress   — same as blockchainTxHash on the data-tx pattern.
 * network           — "POLYGON_AMOY" in production, "MOCK" in dev.
 * polygonScanUrl    — direct link the investor/farmer can click to view the on-chain record.
 *                     e.g. https://amoy.polygonscan.com/tx/0xabc123...
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
        String        network,
        LocalDateTime investedAt
) {
    /**
     * Convenience method — returns the PolygonScan link so the frontend
     * can display it directly as a clickable URL.
     * Returns null when network is MOCK (dev environment).
     */
    public String polygonScanUrl() {
        if (blockchainTxHash == null || blockchainTxHash.startsWith("BLOCKCHAIN_ERROR")
                || "MOCK".equals(network)) {
            return null;
        }
        return "https://amoy.polygonscan.com/tx/" + blockchainTxHash;
    }
}
