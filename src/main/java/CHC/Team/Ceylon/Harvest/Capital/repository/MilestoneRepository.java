package CHC.Team.Ceylon.Harvest.Capital.repository;

import CHC.Team.Ceylon.Harvest.Capital.entity.Milestone;
import CHC.Team.Ceylon.Harvest.Capital.entity.Milestone.MilestoneStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repository for Milestone entities.
 * All queries follow the existing pattern used in InvestmentRepository.
 */
public interface MilestoneRepository extends JpaRepository<Milestone, Long> {

    /**
     * Fetch only APPROVED milestones for a given land, sorted latest first.
     * Used by investors — PENDING and REJECTED are intentionally excluded.
     *
     * AC-2, AC-5: Only APPROVED milestones returned.
     * AC-4 (sorted): milestoneDate DESC = latest first.
     */
    @Query("""
            SELECT m FROM Milestone m
            WHERE m.land.landId = :landId
              AND m.status = :status
            ORDER BY m.milestoneDate DESC
            """)
    List<Milestone> findByLandIdAndStatus(
            @Param("landId") Long landId,
            @Param("status") MilestoneStatus status);

    /**
     * Check that the investor actually has an investment in this land.
     * Used to prevent an investor from querying milestones of a project
     * they haven't funded.
     */
    @Query("""
            SELECT COUNT(i) > 0 FROM Investment i
            WHERE i.investor.userId = :userId
              AND i.land.landId    = :landId
            """)
    boolean existsByInvestorAndLand(
            @Param("userId") Long userId,
            @Param("landId") Long landId);
}
