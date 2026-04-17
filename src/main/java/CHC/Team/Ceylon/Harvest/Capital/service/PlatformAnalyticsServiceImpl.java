package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.dto.PlatformAnalyticsDTO;
import CHC.Team.Ceylon.Harvest.Capital.dto.PlatformAnalyticsDTO.ActiveUsersByRoleDTO;
import CHC.Team.Ceylon.Harvest.Capital.dto.PlatformAnalyticsDTO.InvestmentDistributionItemDTO;
import CHC.Team.Ceylon.Harvest.Capital.dto.PlatformAnalyticsDTO.ProjectStatsDTO;
import CHC.Team.Ceylon.Harvest.Capital.enums.AccountStatus;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.exception.AdminDashboardException;
import CHC.Team.Ceylon.Harvest.Capital.repository.InvestmentRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.LandRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Live-database implementation of {@link PlatformAnalyticsService}.
 *
 * <p>All data is read from the live database on every call (AC-6).
 * No values are hardcoded.
 *
 * <p>Acceptance criteria fulfilled:
 * <ul>
 *   <li>AC-2  – {@link InvestmentRepository#sumTotalInvestmentPlatformWide()}</li>
 *   <li>AC-3  – {@link UserRepository#countByRoleAndAccountStatus(Role, AccountStatus)}</li>
 *   <li>AC-4  – {@link LandRepository#countActiveLands()},
 *               {@link LandRepository#countFundedLands()},
 *               {@link LandRepository#countCompletedLands()}</li>
 *   <li>AC-5  – {@link InvestmentRepository#findInvestmentDistributionPerLand()}</li>
 *   <li>AC-6  – all queries target the live database; no mocking or hardcoding</li>
 * </ul>
 */
@Service
public class PlatformAnalyticsServiceImpl implements PlatformAnalyticsService {

    private final UserRepository       userRepository;
    private final InvestmentRepository investmentRepository;
    private final LandRepository       landRepository;

    public PlatformAnalyticsServiceImpl(
            UserRepository       userRepository,
            InvestmentRepository investmentRepository,
            LandRepository       landRepository) {
        this.userRepository       = userRepository;
        this.investmentRepository = investmentRepository;
        this.landRepository       = landRepository;
    }

    /**
     * {@inheritDoc}
     *
     * <p>All database calls are executed within a single read-only transaction
     * so the snapshot is consistent across queries.
     */
    @Override
    @Transactional(readOnly = true)
    public PlatformAnalyticsDTO getAnalytics() {
        try {
            // ── AC-2: Total investment ────────────────────────────────────────
            BigDecimal totalInvestment = investmentRepository.sumTotalInvestmentPlatformWide();
            if (totalInvestment == null) {
                totalInvestment = BigDecimal.ZERO;
            }

            // ── AC-3: Active users by role ────────────────────────────────────
            long activeFarmers   = userRepository.countByRoleAndAccountStatus(
                    Role.FARMER,   AccountStatus.ACTIVE);
            long activeInvestors = userRepository.countByRoleAndAccountStatus(
                    Role.INVESTOR, AccountStatus.ACTIVE);
            long activeAuditors  = userRepository.countByRoleAndAccountStatus(
                    Role.AUDITOR,  AccountStatus.ACTIVE);

            ActiveUsersByRoleDTO activeUsersByRole =
                    new ActiveUsersByRoleDTO(activeFarmers, activeInvestors, activeAuditors);

            // ── AC-4: Project / land status counts ────────────────────────────
            long activeLands    = landRepository.countActiveLands();
            long fundedLands    = landRepository.countFundedLands();
            long completedLands = landRepository.countCompletedLands();

            ProjectStatsDTO projectStats =
                    new ProjectStatsDTO(activeLands, fundedLands, completedLands);

            // ── AC-5: Investment distribution for chart ───────────────────────
            List<InvestmentDistributionItemDTO> distribution =
                    investmentRepository.findInvestmentDistributionPerLand()
                            .stream()
                            .map(this::toDistributionItem)
                            .toList();

            return new PlatformAnalyticsDTO(
                    totalInvestment,
                    activeUsersByRole,
                    projectStats,
                    distribution);

        } catch (Exception ex) {
            throw new AdminDashboardException(
                    "Unable to load platform analytics. Please try again later.", ex);
        }
    }

    /**
     * Maps a raw Object[] from {@code findInvestmentDistributionPerLand()}
     * to a typed {@link InvestmentDistributionItemDTO}.
     *
     * <p>Column order guaranteed by the JPQL SELECT clause:
     * [0] land_id (Long), [1] project_name (String), [2] total_invested (BigDecimal)
     */
    private InvestmentDistributionItemDTO toDistributionItem(Object[] row) {
        Long       landId        = (Long)       row[0];
        String     projectName   = (String)     row[1];
        BigDecimal totalInvested = (BigDecimal) row[2];
        return new InvestmentDistributionItemDTO(landId, projectName, totalInvested);
    }
}
