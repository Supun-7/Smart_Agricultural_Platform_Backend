package CHC.Team.Ceylon.Harvest.Capital.dto.farmer;

import java.math.BigDecimal;
import java.util.List;

/**
 * AC-1 / AC-2 — Full financial report payload returned by
 * GET /api/farmer/financial-report.
 *
 * <p>The funding figures are sourced from the Investment table
 * (amounts invested per land owned by this farmer) and cross-verified
 * against the Ledger table where applicable, satisfying AC-5.</p>
 */
public record FarmerFinancialReportResponse(

        /** Farmer's user ID. */
        Long farmerId,

        /** Farmer's display name. */
        String farmerName,

        /**
         * AC-2 — Grand total of all funding received across every project/land
         * this farmer owns. Derived from SUM(investment.amountInvested) for
         * all investments on this farmer's lands.
         */
        BigDecimal totalFundingReceived,

        /**
         * AC-2 — Per-project breakdown so the farmer can see which
         * land listing attracted how much funding.
         */
        List<ProjectFundingSummary> projectFundingSummaries,

        /**
         * Ledger-level transaction history for audit/transparency.
         * Satisfies AC-5 — values from the Ledger table are surfaced here.
         */
        List<LedgerEntryDto> ledgerEntries,

        // ── Yield section (convenience — also available via /yield) ──────────

        /** Total kilograms harvested across all submissions. */
        BigDecimal totalYieldKg,

        /** Number of yield submissions recorded. */
        long yieldSubmissionCount,

        /** Most recent yield entries (newest first, up to 10). */
        List<YieldRecordResponse> recentYieldHistory
) {

    /**
     * AC-2 — Funding figures for a single project / land listing.
     * Sourced from the Investment table via InvestmentRepository.sumAmountByLandId.
     */
    public record ProjectFundingSummary(
            Long       landId,
            String     projectName,
            String     location,
            String     cropType,
            BigDecimal totalFundingReceived,
            int        investorCount,
            int        progressPercentage
    ) {}

    /**
     * AC-5 — A single row from the Ledger table, exposed so the farmer can
     * reconcile funding amounts against actual wallet credit events.
     */
    public record LedgerEntryDto(
            Long       ledgerId,
            String     transactionType,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String     gateway,
            String     gatewayReference,
            String     createdAt
    ) {}
}
