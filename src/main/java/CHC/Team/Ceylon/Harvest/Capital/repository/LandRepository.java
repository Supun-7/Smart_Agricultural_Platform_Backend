package CHC.Team.Ceylon.Harvest.Capital.repository;

import CHC.Team.Ceylon.Harvest.Capital.entity.Land;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LandRepository extends JpaRepository<Land, Long> {

    List<Land> findAllByIsActiveTrue();

    List<Land> findByIsActiveTrueOrderByCreatedAtDesc();

    List<Land> findByProjectNameIgnoreCase(String projectName);

    List<Land> findByFarmerUserUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByFarmerUserUserIdAndProjectNameIgnoreCaseAndLocationIgnoreCaseAndIsActiveTrue(
            Long farmerUserId,
            String projectName,
            String location);

    // ── AC-4: Project status counts ──────────────────────────────────────────

    /**
     * AC-4 – Lands that are still open for investment.
     * Condition: is_active = true AND progress_percentage < 100
     */
    @Query("SELECT COUNT(l) FROM Land l WHERE l.isActive = true AND l.progressPercentage < 100")
    long countActiveLands();

    /**
     * AC-4 – Lands that have reached their funding target (fully funded).
     * Condition: is_active = true AND progress_percentage = 100
     */
    @Query("SELECT COUNT(l) FROM Land l WHERE l.isActive = true AND l.progressPercentage = 100")
    long countFundedLands();

    /**
     * AC-4 – Lands that have been closed / harvested (soft-deleted).
     * Condition: is_active = false
     */
    @Query("SELECT COUNT(l) FROM Land l WHERE l.isActive = false")
    long countCompletedLands();
}
