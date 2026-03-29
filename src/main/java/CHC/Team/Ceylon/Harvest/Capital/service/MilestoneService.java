package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.dto.MilestoneDetailResponse;
import CHC.Team.Ceylon.Harvest.Capital.dto.MilestoneSummaryResponse;

import java.util.List;

public interface MilestoneService {

    List<MilestoneSummaryResponse> getPendingMilestones();

    MilestoneDetailResponse getMilestoneDetail(Long milestoneId);

    MilestoneDetailResponse approveMilestone(Long milestoneId, Long auditorId);

    MilestoneDetailResponse rejectMilestone(Long milestoneId, Long auditorId, String reason);

    List<MilestoneDetailResponse> getFarmerMilestones(Long farmerUserId);

    List<MilestoneDetailResponse> getApprovedMilestonesForProjects(List<String> projectNames);
}
