package CHC.Team.Ceylon.Harvest.Capital.dto.auditor;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * AC-3: Request body for assigning or updating a farmer's compliance score.
 *
 * Scoring criteria (AC-1 — approved rules):
 *   Component                         Weight
 *   ─────────────────────────────────────────
 *   Milestone update frequency          40 pts
 *   Evidence quality                    40 pts
 *   Timeliness of submissions           20 pts
 *   ─────────────────────────────────────────
 *   Total possible                     100 pts
 */
public record ComplianceScoreRequest(

    @NotNull(message = "Compliance score is required")
    @DecimalMin(value = "0.00", message = "Score must be at least 0")
    @DecimalMax(value = "100.00", message = "Score must not exceed 100")
    BigDecimal score,

    /** Optional free-text justification from the auditor. */
    String notes
) {}
