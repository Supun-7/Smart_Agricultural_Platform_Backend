package CHC.Team.Ceylon.Harvest.Capital.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Represents a milestone for a funded land/project.
 * A milestone can be PENDING, APPROVED, or REJECTED.
 * Investors can only see APPROVED milestones.
 *
 * Hibernate will auto-create the "milestones" table on startup
 * because spring.jpa.hibernate.ddl-auto=update.
 */
@Entity
@Table(name = "milestones")
public class Milestone {

    public enum MilestoneStatus {
        PENDING,
        APPROVED,
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "milestone_id")
    private Long milestoneId;

    /** The land (project) this milestone belongs to */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "land_id", nullable = false)
    private Land land;

    /** Progress percentage at this milestone, e.g. 25, 50, 75, 100 */
    @Column(name = "progress_percentage", nullable = false)
    private Integer progressPercentage;

    /** Auditor/admin notes describing what was achieved */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /** The date this milestone was recorded */
    @Column(name = "milestone_date", nullable = false)
    private LocalDate milestoneDate;

    /** PENDING / APPROVED / REJECTED */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MilestoneStatus status;

    // ── Getters & Setters ──────────────────────────────────────────────────

    public Long getMilestoneId() { return milestoneId; }
    public void setMilestoneId(Long milestoneId) { this.milestoneId = milestoneId; }

    public Land getLand() { return land; }
    public void setLand(Land land) { this.land = land; }

    public Integer getProgressPercentage() { return progressPercentage; }
    public void setProgressPercentage(Integer progressPercentage) {
        this.progressPercentage = progressPercentage;
    }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDate getMilestoneDate() { return milestoneDate; }
    public void setMilestoneDate(LocalDate milestoneDate) {
        this.milestoneDate = milestoneDate;
    }

    public MilestoneStatus getStatus() { return status; }
    public void setStatus(MilestoneStatus status) { this.status = status; }
}
