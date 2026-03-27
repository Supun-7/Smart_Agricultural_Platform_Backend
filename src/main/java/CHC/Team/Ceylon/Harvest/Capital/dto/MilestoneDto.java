package CHC.Team.Ceylon.Harvest.Capital.dto;

import java.time.LocalDate;

/**
 * Data Transfer Object for a single APPROVED milestone.
 * Sent to the investor frontend.
 *
 * AC-3: includes progressPercentage, notes, date, approvalStatus.
 * Uses Java record (same style as FundedLandDto in this project).
 */
public record MilestoneDto(
        Long   milestoneId,
        int    progressPercentage,
        String notes,
        LocalDate date,
        String approvalStatus     // will always be "APPROVED" for the investor view
) {}
