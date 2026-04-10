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

    /**
     * Appends the given Supabase Storage URLs to the milestone's evidence file
     * list.
     * Only the farmer who owns the milestone may attach files, and only while
     * the milestone is still PENDING.
     *
     * AC-4: links uploaded file URLs to the milestone record.
     *
     * @param milestoneId  the target milestone
     * @param farmerUserId the authenticated farmer's user ID (ownership check)
     * @param fileUrls     one or more public Supabase Storage URLs
     * @return the updated milestone detail (including all evidence files)
     */
    MilestoneDetailResponse attachEvidenceFiles(Long milestoneId, Long farmerUserId, List<String> fileUrls);
}
