package CHC.Team.Ceylon.Harvest.Capital.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AC-3 / AC-4 — Stores a single harvest yield entry submitted by a farmer.
 * Records are append-only; each submission captures the harvest date,
 * the kg yield amount, and an optional note for context.
 */
@Entity
@Table(name = "yield_records")
public class YieldRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "yield_id")
    private Long yieldId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "farmer_user_id", nullable = false)
    private User farmerUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "land_id")
    private Land land;

    /** Harvest yield in kilograms. */
    @Column(name = "yield_amount_kg", nullable = false, precision = 19, scale = 2)
    private BigDecimal yieldAmountKg;

    /** The date the harvest actually took place. */
    @Column(name = "harvest_date", nullable = false)
    private LocalDate harvestDate;

    /** Optional farmer note about this harvest (e.g. weather, pest notes). */
    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ── Constructors ──────────────────────────────────────────────────────────

    public YieldRecord() {}

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long getYieldId()                    { return yieldId; }
    public void setYieldId(Long yieldId)        { this.yieldId = yieldId; }

    public User getFarmerUser()                 { return farmerUser; }
    public void setFarmerUser(User farmerUser)  { this.farmerUser = farmerUser; }

    public Land getLand()                       { return land; }
    public void setLand(Land land)              { this.land = land; }

    public BigDecimal getYieldAmountKg()                        { return yieldAmountKg; }
    public void setYieldAmountKg(BigDecimal yieldAmountKg)      { this.yieldAmountKg = yieldAmountKg; }

    public LocalDate getHarvestDate()                           { return harvestDate; }
    public void setHarvestDate(LocalDate harvestDate)           { this.harvestDate = harvestDate; }

    public String getNotes()                    { return notes; }
    public void setNotes(String notes)          { this.notes = notes; }

    public LocalDateTime getCreatedAt()         { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
