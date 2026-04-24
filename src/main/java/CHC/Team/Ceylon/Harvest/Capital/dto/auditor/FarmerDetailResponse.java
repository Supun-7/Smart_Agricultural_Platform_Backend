package CHC.Team.Ceylon.Harvest.Capital.dto.auditor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Full farmer application detail for the auditor review screen.
 */
public record FarmerDetailResponse(
        String id,
        // User info
        Long userId,
        String email,
        // Personal details
        String farmerName,
        String surname,
        String familyName,
        String address,
        String farmAddress,
        Integer yearStarted,
        // Farm details
        String nicNumber,
        String farmLocation,
        BigDecimal landSizeAcres,
        String cropTypes,
        String landMeasurements,
        // Document URLs
        String nicFrontUrl,
        String nicBackUrl,
        String landPhotoUrls,
        // Status
        String status,
        String rejectionReason,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt
) {}
