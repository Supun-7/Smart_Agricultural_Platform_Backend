package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.dto.PlatformAnalyticsDTO;

/**
 * Contract for aggregating platform-wide analytics data.
 *
 * <p>Fulfilled by {@link PlatformAnalyticsServiceImpl}, which reads
 * exclusively from the live database (AC-6 – no hardcoded data).
 *
 * <p>Acceptance criteria covered:
 * <ul>
 *   <li>AC-2 – total platform investment</li>
 *   <li>AC-3 – active user counts by role (Farmer, Investor, Auditor)</li>
 *   <li>AC-4 – project/land status counts (active, funded, completed)</li>
 *   <li>AC-5 – per-land investment distribution for chart rendering</li>
 * </ul>
 */
public interface PlatformAnalyticsService {

    /**
     * Aggregates and returns a live platform-wide analytics snapshot.
     *
     * @return populated {@link PlatformAnalyticsDTO}
     * @throws CHC.Team.Ceylon.Harvest.Capital.exception.AdminDashboardException
     *         if any database error occurs during aggregation
     */
    PlatformAnalyticsDTO getAnalytics();
}
