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
     */
    @Query("SELECT i FROM Investment i JOIN FETCH i.investor JOIN FETCH i.land " +
           "WHERE i.land.farmerUser.userId = :farmerUserId " +
           "ORDER BY i.investmentDate DESC")
    List<Investment> findAllByFarmerUserIdWithInvestor(@Param("farmerUserId") Long farmerUserId);

    // ── Platform-wide queries ─────────────────────────────────────────────────

    @Query("SELECT COALESCE(SUM(i.amountInvested), 0) FROM Investment i WHERE i.land.landId = :landId")
    BigDecimal sumAmountByLandId(@Param("landId") Long landId);

    @Query("SELECT i FROM Investment i JOIN FETCH i.land WHERE i.land.landId = :landId")
    List<Investment> findAllByLandIdWithLand(@Param("landId") Long landId);

    @Query("SELECT COALESCE(SUM(i.amountInvested), 0) FROM Investment i")
    BigDecimal sumTotalInvestmentPlatformWide();
}
