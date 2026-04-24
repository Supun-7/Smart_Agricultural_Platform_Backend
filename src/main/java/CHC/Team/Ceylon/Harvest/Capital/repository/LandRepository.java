package CHC.Team.Ceylon.Harvest.Capital.repository;

import CHC.Team.Ceylon.Harvest.Capital.entity.Land;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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

    // ── Auditor review queries ────────────────────────────────────────────────

    /**
     * Fetch all lands, eagerly joining the farmer user to prevent
     * LazyInitializationException when building the summary response.
     */
    @Query("SELECT l FROM Land l LEFT JOIN FETCH l.farmerUser ORDER BY l.createdAt DESC")
    List<Land> findAllWithFarmer();

    /**
     * Fetch a single land by id, eagerly joining the farmer user.
     * Used by the auditor project detail endpoint.
     */
    @Query("SELECT l FROM Land l LEFT JOIN FETCH l.farmerUser WHERE l.landId = :landId")
    Optional<Land> findByIdWithFarmer(@Param("landId") Long landId);

    /**
     * Fetch all lands with a given review status, eagerly joining the farmer
     * user so the auditor dashboard never hits a LazyInitializationException.
     */
    @Query("SELECT l FROM Land l LEFT JOIN FETCH l.farmerUser WHERE l.reviewStatus = :status ORDER BY l.createdAt DESC")
    List<Land> findByReviewStatusWithFarmer(@Param("status") VerificationStatus status);

    /**
     * Fetch all lands reviewed by a specific auditor, newest first.
     * Used for the auditor's full decision history.
     */
    @Query("SELECT l FROM Land l LEFT JOIN FETCH l.farmerUser WHERE l.reviewedBy.userId = :auditorId ORDER BY l.reviewedAt DESC")
    List<Land> findByReviewedByUserIdOrderByReviewedAtDesc(@Param("auditorId") Long auditorId);
}
