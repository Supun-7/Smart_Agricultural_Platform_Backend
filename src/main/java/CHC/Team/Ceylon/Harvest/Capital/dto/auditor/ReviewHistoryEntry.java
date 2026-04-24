package CHC.Team.Ceylon.Harvest.Capital.dto.auditor;

import java.time.LocalDateTime;

/**
 * A single entry in the auditor's full review history.
 * Covers KYC approvals/rejections, farmer application decisions, and project decisions.
 */
public record ReviewHistoryEntry(
        String referenceId,       // KYC id / farmer application id / land id (as string)
        String reviewType,        // "KYC" | "FARMER_APPLICATION" | "PROJECT"
        String subjectName,       // Investor / farmer full name or project name
        String decision,          // "APPROVED" | "REJECTED"
        String rejectionReason,   // null when approved
        LocalDateTime reviewedAt
) {}
