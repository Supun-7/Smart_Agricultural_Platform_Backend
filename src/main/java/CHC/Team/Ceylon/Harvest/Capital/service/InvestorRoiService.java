package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.entity.Investment;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface InvestorRoiService {
    Map<String, Object> buildInvestmentRoiMetrics(Investment investment);
    void syncSnapshotsForInvestor(Long userId);
    void syncSnapshotsForLand(Long landId);
    void syncSnapshotsForLand(Long landId, LocalDate snapshotDate);
    void recordSnapshot(Investment investment, LocalDate snapshotDate);
    Map<String, Object> getRoiHistory(Long userId);
    Map<String, Object> buildPortfolioRoiSummary(List<Investment> investments);
}
