package CHC.Team.Ceylon.Harvest.Capital.dto;

import CHC.Team.Ceylon.Harvest.Capital.enums.MilestoneStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record MilestoneDetailResponse(
        Long id,
        String farmerName,
        String farmerEmail,
        String projectName,
        Integer progressPercentage,
        String notes,
        LocalDate milestoneDate,
        MilestoneStatus status,
        String rejectionReason,
        LocalDateTime reviewedAt,
        String reviewedBy,
        LocalDateTime createdAt,
        List<EvidenceFileResponse> evidenceFiles
) {
}
