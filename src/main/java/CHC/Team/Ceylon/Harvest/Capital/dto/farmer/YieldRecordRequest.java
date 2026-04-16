package CHC.Team.Ceylon.Harvest.Capital.dto.farmer;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * AC-3 — Request body for the POST /api/farmer/yield endpoint.
 * The farmer submits the amount harvested (kg) and the harvest date.
 * Linking to a specific land listing is optional but recommended.
 */
public record YieldRecordRequest(

        /**
         * The land this harvest belongs to.
         * Optional — a farmer may record a yield without linking it to a specific listing.
         */
        Long landId,

        /**
         * Harvest amount in kilograms. Must be a positive value.
         */
        @NotNull(message = "Yield amount is required")
        @DecimalMin(value = "0.01", message = "Yield amount must be greater than zero")
        BigDecimal yieldAmountKg,

        /**
         * The actual date the harvest was collected.
         * Cannot be a future date.
         */
        @NotNull(message = "Harvest date is required")
        @PastOrPresent(message = "Harvest date cannot be in the future")
        LocalDate harvestDate,

        /**
         * Optional notes (e.g. weather conditions, pest observations, quality notes).
         */
        @Size(max = 1000, message = "Notes must not exceed 1000 characters")
        String notes
) {}
