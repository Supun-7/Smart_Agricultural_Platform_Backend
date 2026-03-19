package CHC.Team.Ceylon.Harvest.Capital.dto;

import java.math.BigDecimal;

public record FundedLandDto(
        Long projectId,
        String projectName,
        String landName,
        String farmLocation,
        BigDecimal investmentAmount,
        BigDecimal projectProgress
) {
}
