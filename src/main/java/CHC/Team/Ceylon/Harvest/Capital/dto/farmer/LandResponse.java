package CHC.Team.Ceylon.Harvest.Capital.dto.farmer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LandResponse(
        Long landId,
        String projectName,
        String location,
        BigDecimal sizeAcres,
        String cropType,
        String description,
        String imageUrls,
        BigDecimal totalValue,
        BigDecimal minimumInvestment,
        Integer progressPercentage,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long farmerId,
        String farmerName
) {
}
