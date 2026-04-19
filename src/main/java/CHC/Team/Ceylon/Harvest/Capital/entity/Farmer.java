package CHC.Team.Ceylon.Harvest.Capital.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "farmers")
public class Farmer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "farmer_id")
    private Long farmerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "land_name")
    private String landName;

    @Column(name = "land_location")
    private String landLocation;

    @Column(name = "total_investment")
    private BigDecimal totalInvestment;

    @Column(name = "level")
    private Integer level;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── AC-2: Compliance scoring fields ─────────────────────────────────────
    /**
     * AC-2: Compliance score assigned by an auditor.
     * Range: 0.00 – 100.00.  NULL = not yet scored.
     * Scoring criteria (AC-1):
     *   - Milestone update frequency  (0–40 pts)
     *   - Evidence quality            (0–40 pts)
     *   - Timeliness of submissions   (0–20 pts)
     */
    @Column(name = "compliance_score", precision = 5, scale = 2)
    private BigDecimal complianceScore;

    /** AC-1: Free-text notes the auditor can attach to justify the score. */
    @Column(name = "compliance_notes", columnDefinition = "TEXT")
    private String complianceNotes;

    /** Timestamp of the most recent scoring action. */
    @Column(name = "compliance_scored_at")
    private LocalDateTime complianceScoredAt;

    /** The auditor (User) who last set the score. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compliance_scored_by")
    private User complianceScoredBy;

    // ── Getters / Setters ────────────────────────────────────────────────────

    public Long getFarmerId() { return farmerId; }
    public void setFarmerId(Long farmerId) { this.farmerId = farmerId; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getLandName() { return landName; }
    public void setLandName(String landName) { this.landName = landName; }

    public String getLandLocation() { return landLocation; }
    public void setLandLocation(String landLocation) { this.landLocation = landLocation; }

    public BigDecimal getTotalInvestment() { return totalInvestment; }
    public void setTotalInvestment(BigDecimal totalInvestment) { this.totalInvestment = totalInvestment; }

    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public BigDecimal getComplianceScore() { return complianceScore; }
    public void setComplianceScore(BigDecimal complianceScore) { this.complianceScore = complianceScore; }

    public String getComplianceNotes() { return complianceNotes; }
    public void setComplianceNotes(String complianceNotes) { this.complianceNotes = complianceNotes; }

    public LocalDateTime getComplianceScoredAt() { return complianceScoredAt; }
    public void setComplianceScoredAt(LocalDateTime complianceScoredAt) { this.complianceScoredAt = complianceScoredAt; }

    public User getComplianceScoredBy() { return complianceScoredBy; }
    public void setComplianceScoredBy(User complianceScoredBy) { this.complianceScoredBy = complianceScoredBy; }
}
