package CHC.Team.Ceylon.Harvest.Capital.dto.auditor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AC-4: Returned to auditor and admin dashboards after a score is saved or fetched.
 */
public record ComplianceScoreResponse(
    Long farmerId,
    Long userId,
    String farmerFullName,
    String farmerEmail,
    BigDecimal complianceScore,       // null  = not yet scored
    String     complianceNotes,
    LocalDateTime complianceScoredAt,
    String     scoredByFullName       // null if not yet scored
) {}
