package CHC.Team.Ceylon.Harvest.Capital.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Top-level response envelope returned by GET /api/admin/dashboard.
 *
 * Designed to support clean dashboard rendering:
 *   - Counters for summary cards
 *   - Full lists for data tables
 *   - BigDecimal for precise monetary display
 */
public class AdminDashboardResponseDTO {

    /** Total number of users whose role is FARMER. */
    private int totalFarmers;

    /** Total number of users whose role is INVESTOR. */
    private int totalInvestors;

    /**
     * Sum of all investment amounts recorded in the investments table.
     * Uses BigDecimal to avoid floating-point precision issues.
     */
    private BigDecimal totalInvestment;

    /** Full list of farmer users mapped to lightweight UserDTO objects. */
    private List<UserDTO> farmers;

    /** Full list of investor users mapped to lightweight UserDTO objects. */
    private List<UserDTO> investors;

    // ── Constructors ──────────────────────────────────────────────────────────

    public AdminDashboardResponseDTO() {
    }

    public AdminDashboardResponseDTO(
            int totalFarmers,
            int totalInvestors,
            BigDecimal totalInvestment,
            List<UserDTO> farmers,
            List<UserDTO> investors) {
        this.totalFarmers     = totalFarmers;
        this.totalInvestors   = totalInvestors;
        this.totalInvestment  = totalInvestment;
        this.farmers          = farmers;
        this.investors        = investors;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public int getTotalFarmers() {
        return totalFarmers;
    }

    public void setTotalFarmers(int totalFarmers) {
        this.totalFarmers = totalFarmers;
    }

    public int getTotalInvestors() {
        return totalInvestors;
    }

    public void setTotalInvestors(int totalInvestors) {
        this.totalInvestors = totalInvestors;
    }

    public BigDecimal getTotalInvestment() {
        return totalInvestment;
    }

    public void setTotalInvestment(BigDecimal totalInvestment) {
        this.totalInvestment = totalInvestment;
    }

    public List<UserDTO> getFarmers() {
        return farmers;
    }

    public void setFarmers(List<UserDTO> farmers) {
        this.farmers = farmers;
    }

    public List<UserDTO> getInvestors() {
        return investors;
    }

    public void setInvestors(List<UserDTO> investors) {
        this.investors = investors;
    }
}
