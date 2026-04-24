package CHC.Team.Ceylon.Harvest.Capital.repository;

import CHC.Team.Ceylon.Harvest.Capital.entity.YieldRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Repository for the {@code yield_records} table.
 *
 * <p>Column mapping (schema → entity field):
 * <pre>
 *   yield_id         → yieldId         (PK, BIGSERIAL)
 *   farmer_user_id   → farmerUser.userId (FK → users.user_id)
 *   land_id          → land.landId      (FK → lands.land_id, nullable)
 *   harvest_date     → harvestDate      (DATE NOT NULL)
 *   yield_amount_kg  → yieldAmountKg   (NUMERIC NOT NULL)
 *   notes            → notes            (VARCHAR, nullable)
 *   created_at       → createdAt        (TIMESTAMP DEFAULT now())
 * </pre>
 */
@Repository
public interface YieldRecordRepository extends JpaRepository<YieldRecord, Long> {

    // ── AC-4: Full history ────────────────────────────────────────────────────

    /**
     * AC-4 — All yield submissions for a farmer, newest harvest date first.
     * LEFT JOIN FETCH on land avoids N+1 when the history list renders
     * project names alongside each entry.
     *
     * Maps to:
     *   SELECT * FROM yield_records y
     *   LEFT JOIN lands l ON y.land_id = l.land_id
     *   WHERE y.farmer_user_id = :farmerUserId
     *   ORDER BY y.harvest_date DESC, y.created_at DESC
     */
    @Query("SELECT y FROM YieldRecord y LEFT JOIN FETCH y.land " +
           "WHERE y.farmerUser.userId = :farmerUserId " +
           "ORDER BY y.harvestDate DESC, y.createdAt DESC")
    List<YieldRecord> findByFarmerUserIdOrderByHarvestDateDesc(
            @Param("farmerUserId") Long farmerUserId);

    // ── AC-4: Per-land history ────────────────────────────────────────────────

    /**
     * All yield records for a specific land listing (newest first).
     * Used when the farmer views yield history filtered by a single project.
     *
     * Maps to:
     *   SELECT * FROM yield_records
     *   WHERE farmer_user_id = :farmerUserId AND land_id = :landId
     *   ORDER BY harvest_date DESC, created_at DESC
     */
    @Query("SELECT y FROM YieldRecord y LEFT JOIN FETCH y.land " +
           "WHERE y.farmerUser.userId = :farmerUserId " +
           "  AND y.land.landId = :landId " +
           "ORDER BY y.harvestDate DESC, y.createdAt DESC")
    List<YieldRecord> findByFarmerUserIdAndLandIdOrderByHarvestDateDesc(
            @Param("farmerUserId") Long farmerUserId,
            @Param("landId")       Long landId);

    // ── AC-4: Date-range filtering ────────────────────────────────────────────

    /**
     * Yield records for a farmer within an inclusive harvest date range.
     * Supports date-range filtering on the history list (e.g. "this season").
     *
     * Maps to:
     *   SELECT * FROM yield_records
     *   WHERE farmer_user_id = :farmerUserId
     *     AND harvest_date BETWEEN :from AND :to
     *   ORDER BY harvest_date DESC, created_at DESC
     */
    @Query("SELECT y FROM YieldRecord y LEFT JOIN FETCH y.land " +
           "WHERE y.farmerUser.userId = :farmerUserId " +
           "  AND y.harvestDate BETWEEN :from AND :to " +
           "ORDER BY y.harvestDate DESC, y.createdAt DESC")
    List<YieldRecord> findByFarmerUserIdAndHarvestDateBetween(
            @Param("farmerUserId") Long      farmerUserId,
            @Param("from")         LocalDate from,
            @Param("to")           LocalDate to);

    // ── AC-2 / Financial-report aggregates ───────────────────────────────────

    /**
     * AC-2 — Cumulative yield in kg for a single farmer across all submissions.
     * Used in the financial-report dashboard summary card.
     *
     * Maps to:
     *   SELECT COALESCE(SUM(yield_amount_kg), 0)
     *   FROM yield_records
     *   WHERE farmer_user_id = :farmerUserId
     */
    @Query("SELECT COALESCE(SUM(y.yieldAmountKg), 0) FROM YieldRecord y " +
           "WHERE y.farmerUser.userId = :farmerUserId")
    BigDecimal sumYieldKgByFarmerUserId(@Param("farmerUserId") Long farmerUserId);

    /**
     * Cumulative yield in kg for a specific land listing.
     * Used in the per-project section of the financial report.
     *
     * Maps to:
     *   SELECT COALESCE(SUM(yield_amount_kg), 0)
     *   FROM yield_records
     *   WHERE farmer_user_id = :farmerUserId AND land_id = :landId
     */
    @Query("SELECT COALESCE(SUM(y.yieldAmountKg), 0) FROM YieldRecord y " +
           "WHERE y.farmerUser.userId = :farmerUserId " +
           "  AND y.land.landId = :landId")
    BigDecimal sumYieldKgByFarmerUserIdAndLandId(
            @Param("farmerUserId") Long farmerUserId,
            @Param("landId")       Long landId);

    /**
     * Cumulative yield in kg for a farmer within an inclusive date range.
     * Supports seasonal / period totals in the financial report.
     *
     * Maps to:
     *   SELECT COALESCE(SUM(yield_amount_kg), 0)
     *   FROM yield_records
     *   WHERE farmer_user_id = :farmerUserId
     *     AND harvest_date BETWEEN :from AND :to
     */
    @Query("SELECT COALESCE(SUM(y.yieldAmountKg), 0) FROM YieldRecord y " +
           "WHERE y.farmerUser.userId = :farmerUserId " +
           "  AND y.harvestDate BETWEEN :from AND :to")
    BigDecimal sumYieldKgByFarmerUserIdAndDateRange(
            @Param("farmerUserId") Long      farmerUserId,
            @Param("from")         LocalDate from,
            @Param("to")           LocalDate to);

    // ── Count helpers ─────────────────────────────────────────────────────────

    /**
     * Total number of yield submissions for a farmer.
     * Derived query — maps to:
     *   SELECT COUNT(*) FROM yield_records WHERE farmer_user_id = :farmerUserId
     */
    long countByFarmerUserUserId(Long farmerUserId);

    /**
     * Number of yield submissions for a specific land listing.
     * Derived query — maps to:
     *   SELECT COUNT(*) FROM yield_records
     *   WHERE farmer_user_id = :farmerUserId AND land_id = :landId
     */
    long countByFarmerUserUserIdAndLandLandId(Long farmerUserId, Long landLandId);
}
