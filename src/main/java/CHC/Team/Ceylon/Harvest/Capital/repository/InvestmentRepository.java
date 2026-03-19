package CHC.Team.Ceylon.Harvest.Capital.repository;

import CHC.Team.Ceylon.Harvest.Capital.entity.Investment;
import CHC.Team.Ceylon.Harvest.Capital.entity.Investment.InvestmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface InvestmentRepository extends JpaRepository<Investment, Long> {

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

        @Query("SELECT COALESCE(SUM(i.amountInvested), 0) FROM Investment i WHERE i.land.landId = :landId")
        BigDecimal sumAmountByLandId(@Param("landId") Long landId);
}
