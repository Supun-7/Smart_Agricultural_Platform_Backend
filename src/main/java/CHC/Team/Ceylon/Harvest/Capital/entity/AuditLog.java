package CHC.Team.Ceylon.Harvest.Capital.entity;

import CHC.Team.Ceylon.Harvest.Capital.enums.AuditActionType;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Persists every approve / reject action taken by an auditor on a milestone.
 * Mapped to the audit_log table (CHC-207-1).
 * This entity is INSERT-only — no update or delete operations are ever performed on it.
 */
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_log_auditor_id",   columnList = "auditor_id"),
        @Index(name = "idx_audit_log_milestone_id", columnList = "milestone_id"),
        @Index(name = "idx_audit_log_actioned_at",  columnList = "actioned_at")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_log_id")
    private Long auditLogId;

    /** APPROVED or REJECTED */
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private AuditActionType actionType;

    /** The milestone that was acted upon. */
    @Column(name = "milestone_id", nullable = false)
    private Long milestoneId;

    /**
     * Denormalised snapshot of the farmer's full name at the time of the action.
     * Stored so the history stays accurate even if the user record changes later.
     */
    @Column(name = "farmer_name", nullable = false)
    private String farmerName;

    /** FK to the auditor who performed the action. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "auditor_id", nullable = false)
    private User auditor;

    /** UTC timestamp of when the action was recorded. Set automatically on persist. */
    @Column(name = "actioned_at", nullable = false, updatable = false)
    private LocalDateTime actionedAt;

    @PrePersist
    protected void onPersist() {
        if (this.actionedAt == null) {
            this.actionedAt = LocalDateTime.now();
        }
    }

    // ── Constructors ─────────────────────────────────────────────────────────

    protected AuditLog() { }

    public AuditLog(AuditActionType actionType,
                    Long milestoneId,
                    String farmerName,
                    User auditor) {
        this.actionType  = actionType;
        this.milestoneId = milestoneId;
        this.farmerName  = farmerName;
        this.auditor     = auditor;
    }

    // ── Getters (no setters — immutable after creation) ──────────────────────

    public Long getAuditLogId()       { return auditLogId; }
    public AuditActionType getActionType() { return actionType; }
    public Long getMilestoneId()      { return milestoneId; }
    public String getFarmerName()     { return farmerName; }
    public User getAuditor()          { return auditor; }
    public LocalDateTime getActionedAt() { return actionedAt; }
}
