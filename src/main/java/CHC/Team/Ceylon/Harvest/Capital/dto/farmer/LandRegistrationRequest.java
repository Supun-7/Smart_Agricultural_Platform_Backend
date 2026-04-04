package CHC.Team.Ceylon.Harvest.Capital.dto.farmer;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record LandRegistrationRequest(
        @NotBlank(message = "Land name is required")
        @Size(max = 120, message = "Land name must be 120 characters or less")
        String projectName,

        @NotBlank(message = "Location is required")
        @Size(max = 255, message = "Location must be 255 characters or less")
        String location,

        @NotNull(message = "Land size is required")
        @DecimalMin(value = "0.1", message = "Land size must be greater than 0")
        BigDecimal sizeAcres,

        @NotBlank(message = "Crop type is required")
        @Size(max = 120, message = "Crop type must be 120 characters or less")
        String cropType,

        @NotBlank(message = "Description is required")
        @Size(min = 20, max = 2000, message = "Description must be between 20 and 2000 characters")
        String description,

        @NotBlank(message = "At least one image is required")
        @Pattern(
                regexp = "^(https?://[^,\\s]+)(,https?://[^,\\s]+)*$",
                message = "Images must be valid URL values separated by commas"
        )
        String imageUrls,

        @NotNull(message = "Total value is required")
        @DecimalMin(value = "1.0", message = "Total value must be greater than 0")
        BigDecimal totalValue,

        @NotNull(message = "Minimum investment is required")
        @DecimalMin(value = "1.0", message = "Minimum investment must be greater than 0")
        BigDecimal minimumInvestment
) {
}
