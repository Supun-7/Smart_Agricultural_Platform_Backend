package CHC.Team.Ceylon.Harvest.Capital.dto;

import java.time.LocalDateTime;

public record ApiErrorResponse(
        String message,
        LocalDateTime timestamp
) {
}
