package CHC.Team.Ceylon.Harvest.Capital.dto.auditor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AC-4: Lightweight projection used in auditor / admin farmer compliance list.
 */
public record FarmerComplianceListItem(
    Long farmerId,
    Long userId,
    String farmerFullName,
    String farmerEmail,
    String verificationStatus,
    BigDecimal complianceScore,
    String complianceNotes,
    LocalDateTime complianceScoredAt,
    String scoredByFullName
) {}
