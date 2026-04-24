package CHC.Team.Ceylon.Harvest.Capital.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Platform-wide analytics snapshot returned by
 * {@code GET /api/admin/analytics}.
 *
 * <ul>
 *   <li>AC-2  – {@link #totalInvestment}</li>
 *   <li>AC-3  – {@link #activeUsersByRole}</li>
 *   <li>AC-4  – {@link #projectStats}</li>
 *   <li>AC-5  – {@link #investmentDistribution} (drives the chart)</li>
 * </ul>
 */
public class PlatformAnalyticsDTO {

    // ── AC-2: Total platform investment ──────────────────────────────────────

    /** Sum of all investments across the platform. Never null (defaults to 0). */
    private BigDecimal totalInvestment;

    // ── AC-3: Active users by role ────────────────────────────────────────────

    /** Per-role breakdown of ACTIVE users: Farmer, Investor, Auditor. */
    private ActiveUsersByRoleDTO activeUsersByRole;

    // ── AC-4: Project / land status counts ───────────────────────────────────

    /** Counts of active, funded, and completed lands/projects. */
    private ProjectStatsDTO projectStats;

    // ── AC-5: Investment distribution for chart ───────────────────────────────

    /**
     * Per-land investment totals ordered by amount descending.
     * Used to render a bar / pie chart in the admin UI.
     */
    private List<InvestmentDistributionItemDTO> investmentDistribution;

    // ── Constructor ───────────────────────────────────────────────────────────

    public PlatformAnalyticsDTO() {}

    public PlatformAnalyticsDTO(
            BigDecimal totalInvestment,
            ActiveUsersByRoleDTO activeUsersByRole,
            ProjectStatsDTO projectStats,
            List<InvestmentDistributionItemDTO> investmentDistribution) {
        this.totalInvestment         = totalInvestment;
        this.activeUsersByRole       = activeUsersByRole;
        this.projectStats            = projectStats;
        this.investmentDistribution  = investmentDistribution;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public BigDecimal getTotalInvestment() { return totalInvestment; }
    public void setTotalInvestment(BigDecimal totalInvestment) {
        this.totalInvestment = totalInvestment;
    }

    public ActiveUsersByRoleDTO getActiveUsersByRole() { return activeUsersByRole; }
    public void setActiveUsersByRole(ActiveUsersByRoleDTO activeUsersByRole) {
        this.activeUsersByRole = activeUsersByRole;
    }

    public ProjectStatsDTO getProjectStats() { return projectStats; }
    public void setProjectStats(ProjectStatsDTO projectStats) {
        this.projectStats = projectStats;
    }

    public List<InvestmentDistributionItemDTO> getInvestmentDistribution() {
        return investmentDistribution;
    }
    public void setInvestmentDistribution(
            List<InvestmentDistributionItemDTO> investmentDistribution) {
        this.investmentDistribution = investmentDistribution;
    }

    // ── Nested DTOs ───────────────────────────────────────────────────────────

    /**
     * AC-3 – Active user counts broken down by role.
     * "Active" = accountStatus == ACTIVE.
     */
    public static class ActiveUsersByRoleDTO {

        private long farmers;
        private long investors;
        private long auditors;

        public ActiveUsersByRoleDTO() {}

        public ActiveUsersByRoleDTO(long farmers, long investors, long auditors) {
            this.farmers   = farmers;
            this.investors = investors;
            this.auditors  = auditors;
        }

        public long getFarmers()   { return farmers; }
        public void setFarmers(long farmers)   { this.farmers = farmers; }

        public long getInvestors() { return investors; }
        public void setInvestors(long investors) { this.investors = investors; }

        public long getAuditors()  { return auditors; }
        public void setAuditors(long auditors)  { this.auditors = auditors; }
    }

    /**
     * AC-4 – Count of lands/projects by funding state.
     *
     * <ul>
     *   <li><b>active</b>   – is_active = true  AND progress_percentage &lt; 100</li>
     *   <li><b>funded</b>   – is_active = true  AND progress_percentage = 100</li>
     *   <li><b>completed</b>– is_active = false (project closed / harvested)</li>
     * </ul>
     */
    public static class ProjectStatsDTO {

        private long active;
        private long funded;
        private long completed;

        public ProjectStatsDTO() {}

        public ProjectStatsDTO(long active, long funded, long completed) {
            this.active    = active;
            this.funded    = funded;
            this.completed = completed;
        }

        public long getActive()    { return active; }
        public void setActive(long active)       { this.active = active; }

        public long getFunded()    { return funded; }
        public void setFunded(long funded)       { this.funded = funded; }

        public long getCompleted() { return completed; }
        public void setCompleted(long completed) { this.completed = completed; }
    }

    /**
     * AC-5 – One slice of the investment distribution chart.
     * Each item represents a single land/project and the total
     * amount invested in it.
     */
    public static class InvestmentDistributionItemDTO {

        /** land_id */
        private Long landId;

        /** project_name column from the lands table */
        private String projectName;

        /** COALESCE(SUM(amount_invested), 0) for this land */
        private BigDecimal totalInvested;

        public InvestmentDistributionItemDTO() {}

        public InvestmentDistributionItemDTO(
                Long landId,
                String projectName,
                BigDecimal totalInvested) {
            this.landId        = landId;
            this.projectName   = projectName;
            this.totalInvested = totalInvested;
        }

        public Long getLandId()          { return landId; }
        public void setLandId(Long landId) { this.landId = landId; }

        public String getProjectName()            { return projectName; }
        public void setProjectName(String projectName) { this.projectName = projectName; }

        public BigDecimal getTotalInvested()              { return totalInvested; }
        public void setTotalInvested(BigDecimal totalInvested) { this.totalInvested = totalInvested; }
    }
}
