package CHC.Team.Ceylon.Harvest.Capital.dto.auditor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Full detail of a land/project for auditor review.
 */
public record ProjectDetailResponse(
        Long landId,
        String projectName,
        String location,
        BigDecimal totalValue,
        BigDecimal minimumInvestment,
        Integer progressPercentage,
        Boolean isActive,
        BigDecimal sizeAcres,
        String cropType,
        String description,
        String imageUrls,
        LocalDateTime createdAt,
        // Farmer info
        Long farmerUserId,
        String farmerFullName,
        String farmerEmail,
        String farmerVerificationStatus,
        // Auditor review
        String reviewStatus,
        String rejectionReason
) {}
