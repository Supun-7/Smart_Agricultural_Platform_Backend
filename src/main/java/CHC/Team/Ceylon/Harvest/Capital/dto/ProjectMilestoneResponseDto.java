package CHC.Team.Ceylon.Harvest.Capital.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Full project detail response for an investor.
 *
 * AC-1: returned when an investor navigates to a project detail page.
 * AC-4: includes milestones list for visual timeline / progress bar on frontend.
 *
 * Uses Java record (same style as FundedLandDto in this project).
 */
public record ProjectMilestoneResponseDto(
        Long            landId,
        String          projectName,
        String          location,
        BigDecimal      totalValue,
        BigDecimal      amountInvested,       // this investor's contribution
        int             overallProgress,      // lands.progress_percentage
        List<MilestoneDto> approvedMilestones // AC-2: only APPROVED, latest first
) {}
