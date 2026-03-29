package CHC.Team.Ceylon.Harvest.Capital.dto;

import CHC.Team.Ceylon.Harvest.Capital.enums.MilestoneStatus;

import java.time.LocalDate;

public record MilestoneSummaryResponse(
        Long id,
        String farmerName,
        String projectName,
        Integer progressPercentage,
        String notes,
        LocalDate milestoneDate,
        MilestoneStatus status
) {
}
