package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.dto.AdminDashboardResponseDTO;

/**
 * Contract for the admin dashboard aggregation logic.
 * Keeping the interface separate from the implementation allows
 * easy mocking in tests and future swapping of data sources.
 */
public interface AdminDashboardService {

    /**
     * Aggregates platform-wide data for the admin dashboard.
     *
     * @return a fully populated {@link AdminDashboardResponseDTO}
     * @throws CHC.Team.Ceylon.Harvest.Capital.exception.AdminDashboardException
     *         if any database error occurs during aggregation
     */
    AdminDashboardResponseDTO getDashboardData();
}
