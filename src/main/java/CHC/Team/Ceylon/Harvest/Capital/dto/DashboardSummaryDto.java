package CHC.Team.Ceylon.Harvest.Capital.dto;

import java.math.BigDecimal;

public record DashboardSummaryDto(
        int totalFundedLands,
        BigDecimal totalInvestmentAmount
) {
}
