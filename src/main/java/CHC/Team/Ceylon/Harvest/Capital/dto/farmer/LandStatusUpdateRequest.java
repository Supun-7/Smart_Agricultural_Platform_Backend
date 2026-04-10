package CHC.Team.Ceylon.Harvest.Capital.dto.farmer;

import jakarta.validation.constraints.NotNull;

public record LandStatusUpdateRequest(
        @NotNull(message = "isActive flag is required")
        Boolean isActive
) {
}
