package CHC.Team.Ceylon.Harvest.Capital.repository;

import CHC.Team.Ceylon.Harvest.Capital.entity.Investment;
import CHC.Team.Ceylon.Harvest.Capital.entity.Investment.InvestmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface InvestmentRepository extends JpaRepository<Investment, Long> {

        // ── Investor queries ──────────────────────────────────────────────────────

        @Query("SELECT i FROM Investment i JOIN FETCH i.land WHERE i.investor.userId = :userId")
        List<Investment> findAllByUserIdWithLand(@Param("userId") Long userId);

        @Query("SELECT COALESCE(SUM(i.amountInvested), 0) FROM Investment i WHERE i.investor.userId = :userId")
        BigDecimal sumTotalByUserId(@Param("userId") Long userId);

        @Query("SELECT COALESCE(SUM(i.amountInvested), 0) FROM Investment i WHERE i.investor.userId = :userId AND i.status = :status")
        BigDecimal sumByUserIdAndStatus(@Param("userId") Long userId, @Param("status") InvestmentStatus status);

        @Query("SELECT COUNT(DISTINCT i.land.landId) FROM Investment i WHERE i.investor.userId = :userId")
        Long countDistinctLandsByUserId(@Param("userId") Long userId);

        @Query("SELECT COUNT(DISTINCT i.land.landId) FROM Investment i WHERE i.investor.userId = :userId AND i.status = 'ACTIVE'")
        Long countActiveLandsByUserId(@Param("userId") Long userId);

        // ── Farmer queries ────────────────────────────────────────────────────────

        /**
         * Returns all investments on lands owned by a given farmer.
         * Used by FarmerDashboardServiceImpl to show investors and blockchain links
         * for each land the farmer has listed.
         *
         * Maps to:
         * SELECT i.*, inv.*, l.*
         * FROM investments i
         * JOIN users inv ON i.investor_id = inv.user_id
         * JOIN lands l ON i.land_id = l.land_id
         * WHERE l.farmer_id = :farmerUserId
         * ORDER BY i.investment_date DESC
         */
        @Query("SELECT i FROM Investment i JOIN FETCH i.investor JOIN FETCH i.land " +
                        "WHERE i.land.farmerUser.userId = :farmerUserId " +
                        "ORDER BY i.investmentDate DESC")
        List<Investment> findAllByFarmerUserIdWithInvestor(@Param("farmerUserId") Long farmerUserId);

        /**
         * AC-2 — Total funding received by a farmer across ALL their land listings.
         * This is the platform-total figure shown at the top of the financial report.
         *
         * Maps to:
         * SELECT COALESCE(SUM(i.amount_invested), 0)
         * FROM investments i
         * JOIN lands l ON i.land_id = l.land_id
         * WHERE l.farmer_id = :farmerUserId
         */
        @Query("SELECT COALESCE(SUM(i.amountInvested), 0) FROM Investment i " +
                        "WHERE i.land.farmerUser.userId = :farmerUserId")
        BigDecimal sumTotalFundingByFarmerUserId(@Param("farmerUserId") Long farmerUserId);

        /**
         * AC-2 — Total funding received by a farmer for a SINGLE land listing.
         * Drives the per-project breakdown in the financial report.
         *
         * Maps to:
         * SELECT COALESCE(SUM(amount_invested), 0)
         * FROM investments
         * WHERE land_id = :landId AND investments.investor_id IN (
         * SELECT investor_id FROM investments
         * WHERE land_id = :landId
         * )
         * (Simplified JPQL version below — same result.)
         *
         * Maps to:
         * SELECT COALESCE(SUM(i.amount_invested), 0)
         * FROM investments i
         * JOIN lands l ON i.land_id = l.land_id
         * WHERE l.land_id = :landId AND l.farmer_id = :farmerUserId
         */
        @Query("SELECT COALESCE(SUM(i.amountInvested), 0) FROM Investment i " +
                        "WHERE i.land.landId = :landId " +
                        "  AND i.land.farmerUser.userId = :farmerUserId")
        BigDecimal sumFundingByLandIdAndFarmerUserId(
                        @Param("landId") Long landId,
                        @Param("farmerUserId") Long farmerUserId);

        /**
         * AC-2 — Count of distinct investors who have funded a specific land listing.
         * Used to populate the investorCount field in ProjectFundingSummary.
         *
         * Maps to:
         * SELECT COUNT(DISTINCT investor_id)
         * FROM investments
         * WHERE land_id = :landId
         */
        @Query("SELECT COUNT(DISTINCT i.investor.userId) FROM Investment i " +
                        "WHERE i.land.landId = :landId")
        long countDistinctInvestorsByLandId(@Param("landId") Long landId);

        // ── Platform-wide queries ─────────────────────────────────────────────────

        @Query("SELECT COALESCE(SUM(i.amountInvested), 0) FROM Investment i WHERE i.land.landId = :landId")
        BigDecimal sumAmountByLandId(@Param("landId") Long landId);

        @Query("SELECT i FROM Investment i JOIN FETCH i.land WHERE i.land.landId = :landId")
        List<Investment> findAllByLandIdWithLand(@Param("landId") Long landId);

        @Query("SELECT COALESCE(SUM(i.amountInvested), 0) FROM Investment i")
        BigDecimal sumTotalInvestmentPlatformWide();

        // ── AC-5: Per-land investment distribution for chart ─────────────────────

        /**
         * AC-5 – Returns each land's total invested amount for the distribution chart.
         * Only includes lands that have received at least one investment.
         * Ordered by amount descending so the chart shows the largest slice first.
         *
         * Result columns: [landId (Long), projectName (String), totalInvested
         * (BigDecimal)]
         */
        @Query("""
                        SELECT i.land.landId,
                               i.land.projectName,
                               COALESCE(SUM(i.amountInvested), 0)
                        FROM   Investment i
                        GROUP  BY i.land.landId, i.land.projectName
                        ORDER  BY SUM(i.amountInvested) DESC
                        """)
        List<Object[]> findInvestmentDistributionPerLand();
}