package CHC.Team.Ceylon.Harvest.Capital.dto;

import CHC.Team.Ceylon.Harvest.Capital.enums.AuditActionType;

import java.time.LocalDateTime;

/**
 * Read-only projection returned by GET /api/auditor/history (CHC-207, AC-2).
 *
 * Fields:
 *  auditLogId  — surrogate PK of the log entry
 *  actionType  — APPROVED | REJECTED
 *  milestoneId — the milestone that was reviewed
 *  farmerName  — snapshot of the farmer's name at review time
 *  auditorId   — user_id of the auditor who acted
 *  actionedAt  — UTC timestamp of the action
 */
public record AuditLogResponse(
        Long auditLogId,
        AuditActionType actionType,
        Long milestoneId,
        String farmerName,
        Long auditorId,
        LocalDateTime actionedAt
) { }
