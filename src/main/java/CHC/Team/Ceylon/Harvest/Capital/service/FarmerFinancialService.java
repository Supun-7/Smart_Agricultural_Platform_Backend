package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.dto.farmer.FarmerFinancialReportResponse;
import CHC.Team.Ceylon.Harvest.Capital.dto.farmer.YieldRecordRequest;
import CHC.Team.Ceylon.Harvest.Capital.dto.farmer.YieldRecordResponse;

import java.util.List;

/**
 * Service contract for the Farmer Financial Report and Yield Tracking feature.
 *
 * <ul>
 *   <li>AC-1  — {@link #getFinancialReport} builds the full financial report section.</li>
 *   <li>AC-2  — Report includes per-project and platform-total funding figures.</li>
 *   <li>AC-3  — {@link #submitYield} accepts and persists a new yield entry.</li>
 *   <li>AC-4  — {@link #getYieldHistory} returns all saved yield entries.</li>
 *   <li>AC-5  — Funding figures come from Investment + Ledger tables (see impl).</li>
 * </ul>
 */
public interface FarmerFinancialService {

    /**
     * AC-1 / AC-2 / AC-5
     * Builds the complete financial report for the authenticated farmer:
     * total funding received, per-project breakdown, ledger entries, and
     * a snapshot of recent yield history.
     *
     * @param farmerUserId the authenticated farmer's user ID (from JWT)
     * @return fully populated {@link FarmerFinancialReportResponse}
     */
    FarmerFinancialReportResponse getFinancialReport(Long farmerUserId);

    /**
     * AC-3 / AC-4
     * Validates and persists a new yield record submitted by the farmer.
     * If {@code request.landId()} is provided, the land is verified to belong
     * to this farmer before the record is saved.
     *
     * @param farmerUserId the authenticated farmer's user ID (from JWT)
     * @param request      validated yield submission payload
     * @return the saved yield entry as a response DTO
     */
    YieldRecordResponse submitYield(Long farmerUserId, YieldRecordRequest request);

    /**
     * AC-4
     * Returns the complete yield history for the given farmer, ordered by
     * harvest date descending (most recent first).
     *
     * @param farmerUserId the authenticated farmer's user ID (from JWT)
     * @return list of all yield records for this farmer
     */
    List<YieldRecordResponse> getYieldHistory(Long farmerUserId);
}
