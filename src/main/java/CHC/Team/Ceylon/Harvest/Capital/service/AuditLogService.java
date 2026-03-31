package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.dto.AuditLogResponse;
import CHC.Team.Ceylon.Harvest.Capital.enums.AuditActionType;

import java.util.List;

/**
 * Contract for the audit-log feature (CHC-207).
 */
public interface AuditLogService {

    /**
     * Persists a new log entry immediately after a milestone decision is made (AC-3).
     *
     * @param actionType  APPROVED or REJECTED
     * @param milestoneId ID of the milestone that was reviewed
     * @param farmerName  full name of the farmer who submitted the milestone
     * @param auditorId   user_id of the auditor who acted
     */
    void log(AuditActionType actionType, Long milestoneId, String farmerName, Long auditorId);

    /**
     * Returns the full audit history for a specific auditor, newest first (AC-3, AC-4).
     *
     * @param auditorId user_id of the auditor whose history is requested
     * @return immutable list of log entries
     */
    List<AuditLogResponse> getHistoryForAuditor(Long auditorId);
}
