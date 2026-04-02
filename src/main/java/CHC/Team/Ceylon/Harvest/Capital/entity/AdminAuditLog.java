package CHC.Team.Ceylon.Harvest.Capital.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_audit_logs", indexes = {
        @Index(name = "idx_admin_audit_logs_created_at", columnList = "created_at"),
        @Index(name = "idx_admin_audit_logs_admin_user_id", columnList = "admin_user_id"),
        @Index(name = "idx_admin_audit_logs_target_user_id", columnList = "target_user_id")
})
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_user_id", nullable = false)
    private User adminUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_user_id", nullable = false)
    private User targetUser;

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    @Column(name = "details")
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    protected AdminAuditLog() {
    }

    public AdminAuditLog(User adminUser, User targetUser, String actionType, String details) {
        this.adminUser = adminUser;
        this.targetUser = targetUser;
        this.actionType = actionType;
        this.details = details;
    }

    public Long getId() { return id; }
    public User getAdminUser() { return adminUser; }
    public User getTargetUser() { return targetUser; }
    public String getActionType() { return actionType; }
    public String getDetails() { return details; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
