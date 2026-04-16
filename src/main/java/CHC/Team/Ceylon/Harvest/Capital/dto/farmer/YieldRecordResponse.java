package CHC.Team.Ceylon.Harvest.Capital.dto.farmer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AC-4 — Represents a single saved yield entry returned in the history list
 * and as the body of a successful POST /api/farmer/yield response.
 */
public record YieldRecordResponse(
        Long        yieldId,
        Long        landId,
        String      projectName,
        BigDecimal  yieldAmountKg,
        LocalDate   harvestDate,
        String      notes,
        LocalDateTime createdAt
) {}
