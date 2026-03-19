package CHC.Team.Ceylon.Harvest.Capital.dto;

import java.util.List;

public record FarmerDashboardResponse(
        DashboardSummaryDto summary,
        List<FundedLandDto> fundedLands
) {
}
