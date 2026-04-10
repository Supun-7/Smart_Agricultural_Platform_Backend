package CHC.Team.Ceylon.Harvest.Capital.service.impl;

import CHC.Team.Ceylon.Harvest.Capital.dto.AuditLogResponse;
import CHC.Team.Ceylon.Harvest.Capital.entity.AuditLog;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.AuditActionType;
import CHC.Team.Ceylon.Harvest.Capital.exception.ResourceNotFoundException;
import CHC.Team.Ceylon.Harvest.Capital.repository.AuditLogRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.service.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation of {@link AuditLogService} (CHC-207).
 *
 * <p>Write path: called from {@link MilestoneServiceImpl} after every approve / reject.
 * <p>Read path:  called from {@link AuditorHistoryController} for GET /api/auditor/history.
 */
@Service
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository     userRepository;

    public AuditLogServiceImpl(AuditLogRepository auditLogRepository,
                               UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository     = userRepository;
    }

    /**
     * Creates and persists a new {@link AuditLog} entry (CHC-207-2).
     * Runs in its own transaction so a logging failure never rolls back the
     * milestone approval / rejection that triggered it.
     */
    @Override
    @Transactional
    public void log(AuditActionType actionType,
                    Long milestoneId,
                    String farmerName,
                    Long auditorId) {

        User auditor = userRepository.findById(auditorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Auditor not found while writing audit log: " + auditorId));

        AuditLog entry = new AuditLog(actionType, milestoneId, farmerName, auditor);
        auditLogRepository.save(entry);
    }

    /**
     * Returns all log entries for the given auditor, most-recent first (AC-4).
     */
    @Override
    @Transactional(readOnly = true)
    public List<AuditLogResponse> getHistoryForAuditor(Long auditorId) {
        return auditLogRepository
                .findAllByAuditorIdOrderByActionedAtDesc(auditorId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private AuditLogResponse toResponse(AuditLog entry) {
        return new AuditLogResponse(
                entry.getAuditLogId(),
                entry.getActionType(),
                entry.getMilestoneId(),
                entry.getFarmerName(),
                entry.getAuditor().getUserId(),
                entry.getActionedAt()
        );
    }
}
