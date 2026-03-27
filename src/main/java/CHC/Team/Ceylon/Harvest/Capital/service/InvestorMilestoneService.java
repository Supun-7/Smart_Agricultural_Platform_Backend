package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.dto.ProjectMilestoneResponseDto;

/**
 * Service for the investor milestone feature.
 * A separate interface keeps the new feature isolated from existing services.
 */
public interface InvestorMilestoneService {

    /**
     * Returns the project detail + all APPROVED milestones for a given land,
     * verified that the requesting investor actually funded this land.
     *
     * @param investorUserId the authenticated investor's user_id (from JWT)
     * @param landId         the land/project the investor wants to inspect
     * @return               project info + approved milestones, newest first
     */
    ProjectMilestoneResponseDto getApprovedMilestones(Long investorUserId, Long landId);
}
