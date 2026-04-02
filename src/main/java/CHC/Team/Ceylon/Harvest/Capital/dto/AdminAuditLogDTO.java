package CHC.Team.Ceylon.Harvest.Capital.dto;

import java.time.LocalDateTime;

public record AdminAuditLogDTO(
        Long id,
        String actionType,
        Long adminUserId,
        String adminName,
        Long targetUserId,
        String targetName,
        String targetEmail,
        String details,
        LocalDateTime createdAt
) {}
