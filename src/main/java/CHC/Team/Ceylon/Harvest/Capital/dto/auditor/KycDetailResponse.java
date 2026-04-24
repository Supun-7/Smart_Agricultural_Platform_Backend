package CHC.Team.Ceylon.Harvest.Capital.dto.auditor;

import java.time.LocalDateTime;

/**
 * Full KYC submission detail for the auditor review screen.
 */
public record KycDetailResponse(
        String id,
        // Investor user info
        Long userId,
        String email,
        // Personal details
        String title,
        String firstName,
        String lastName,
        Integer age,
        String nationality,
        String currentOccupation,
        String address,
        String idType,
        String idNumber,
        // Document URLs
        String idFrontUrl,
        String idBackUrl,
        String utilityBillUrl,
        String bankStmtUrl,
        // Status
        String status,
        String rejectionReason,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt
) {}
