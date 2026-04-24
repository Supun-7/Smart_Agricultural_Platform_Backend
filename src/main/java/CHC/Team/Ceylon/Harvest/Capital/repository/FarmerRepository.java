package CHC.Team.Ceylon.Harvest.Capital.repository;

import CHC.Team.Ceylon.Harvest.Capital.entity.Farmer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FarmerRepository extends JpaRepository<Farmer, Long> {

    Optional<Farmer> findByUserUserId(Long userId);

    /**
     * AC-3 / AC-4: Eager-loads user and scoredBy so the service layer
     * never touches a lazy proxy outside a transaction.
     */
    @Query("""
        SELECT f FROM Farmer f
        LEFT JOIN FETCH f.user
        LEFT JOIN FETCH f.complianceScoredBy
        WHERE f.farmerId = :farmerId
        """)
    Optional<Farmer> findByIdWithUserAndScoredBy(@Param("farmerId") Long farmerId);

    /**
     * AC-4: Returns all farmers with user info for list views on dashboards.
     */
    @Query("""
        SELECT f FROM Farmer f
        LEFT JOIN FETCH f.user
        LEFT JOIN FETCH f.complianceScoredBy
        ORDER BY f.farmerId ASC
        """)
    List<Farmer> findAllWithUserAndScoredBy();
}
