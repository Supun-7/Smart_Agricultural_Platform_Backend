package CHC.Team.Ceylon.Harvest.Capital.service;

import java.util.Map;

public interface InvestorDashboardService {

    void validateInvestorBalance(Long investorId, long investmentAmount) throws Exception;

    // AC-1: full dashboard data for the authenticated investor
    Map<String, Object> getDashboard(Long userId);

    // Fills the TODO in /opportunities
    Map<String, Object> getOpportunities();

    // Fills the TODO in /portfolio
    Map<String, Object> getPortfolio(Long userId);

    // Fills the TODO in /reports
    Map<String, Object> getReports(Long userId);
}
