package CHC.Team.Ceylon.Harvest.Capital.dto;

import jakarta.validation.constraints.NotBlank;

public record MilestoneDecisionRequest(
        @NotBlank(message = "Rejection reason is required")
        String reason
) {
}
