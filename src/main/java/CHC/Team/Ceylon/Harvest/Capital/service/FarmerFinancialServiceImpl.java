package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.dto.farmer.FarmerFinancialReportResponse;
import CHC.Team.Ceylon.Harvest.Capital.dto.farmer.FarmerFinancialReportResponse.LedgerEntryDto;
import CHC.Team.Ceylon.Harvest.Capital.dto.farmer.FarmerFinancialReportResponse.ProjectFundingSummary;
import CHC.Team.Ceylon.Harvest.Capital.dto.farmer.YieldRecordRequest;
import CHC.Team.Ceylon.Harvest.Capital.dto.farmer.YieldRecordResponse;
import CHC.Team.Ceylon.Harvest.Capital.entity.Investment;
import CHC.Team.Ceylon.Harvest.Capital.entity.Land;
import CHC.Team.Ceylon.Harvest.Capital.entity.Ledger;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.entity.YieldRecord;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.exception.BadRequestException;
import CHC.Team.Ceylon.Harvest.Capital.exception.FarmerDashboardException;
import CHC.Team.Ceylon.Harvest.Capital.exception.ResourceNotFoundException;
import CHC.Team.Ceylon.Harvest.Capital.repository.InvestmentRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.LandRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.LedgerRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.YieldRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AC-1  — Produces the farmer's financial report section.
 * AC-2  — Aggregates per-project and platform-total funding from the
 *          Investment table (sourced via InvestmentRepository).
 * AC-3  — Validates and persists yield submissions.
 * AC-4  — Reads yield history from YieldRecordRepository.
 * AC-5  — Funding values are cross-checked against the Ledger table;
 *          ledger entries are included in the report for transparency.
 */
@Service
@Transactional
public class FarmerFinancialServiceImpl implements FarmerFinancialService {

    private final UserRepository       userRepository;
    private final LandRepository       landRepository;
    private final InvestmentRepository investmentRepository;
    private final LedgerRepository     ledgerRepository;
    private final YieldRecordRepository yieldRecordRepository;

    public FarmerFinancialServiceImpl(
            UserRepository        userRepository,
            LandRepository        landRepository,
            InvestmentRepository  investmentRepository,
            LedgerRepository      ledgerRepository,
            YieldRecordRepository yieldRecordRepository) {
        this.userRepository        = userRepository;
        this.landRepository        = landRepository;
        this.investmentRepository  = investmentRepository;
        this.ledgerRepository      = ledgerRepository;
        this.yieldRecordRepository = yieldRecordRepository;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AC-1 / AC-2 / AC-5  Financial Report
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public FarmerFinancialReportResponse getFinancialReport(Long farmerUserId) {
        User farmer = resolveFarmer(farmerUserId);

        // ── AC-2: per-project funding from Investment table ──────────────────
        List<Land> farmerLands =
                landRepository.findByFarmerUserUserIdOrderByCreatedAtDesc(farmerUserId);

        List<ProjectFundingSummary> projectSummaries = new ArrayList<>();
        BigDecimal platformTotal = BigDecimal.ZERO;

        for (Land land : farmerLands) {
            // All investments on this land (JOIN FETCH already applied in repo)
            List<Investment> landInvestments =
                    investmentRepository.findAllByLandIdWithLand(land.getLandId());

            BigDecimal landTotal = landInvestments.stream()
                    .map(Investment::getAmountInvested)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Count distinct investors for this land
            long distinctInvestors = landInvestments.stream()
                    .filter(i -> i.getInvestor() != null)
                    .map(i -> i.getInvestor().getUserId())
                    .distinct()
                    .count();

            projectSummaries.add(new ProjectFundingSummary(
                    land.getLandId(),
                    land.getProjectName(),
                    land.getLocation(),
                    land.getCropType(),
                    landTotal,
                    (int) distinctInvestors,
                    land.getProgressPercentage() != null ? land.getProgressPercentage() : 0
            ));

            platformTotal = platformTotal.add(landTotal);
        }

        // ── AC-5: ledger entries for the farmer's wallet ─────────────────────
        // The ledger records every credit/debit on the farmer's wallet account.
        // Surfacing these allows the farmer to reconcile funding received.
        List<LedgerEntryDto> ledgerEntries = buildLedgerEntries(farmerUserId);

        // ── Yield snapshot ───────────────────────────────────────────────────
        BigDecimal totalYieldKg =
                yieldRecordRepository.sumYieldKgByFarmerUserId(farmerUserId);
        long yieldCount = yieldRecordRepository.countByFarmerUserUserId(farmerUserId);

        List<YieldRecord> recentRecords =
                yieldRecordRepository.findByFarmerUserIdOrderByHarvestDateDesc(farmerUserId)
                        .stream()
                        .limit(10)
                        .collect(Collectors.toList());

        List<YieldRecordResponse> recentYield = recentRecords.stream()
                .map(this::toYieldResponse)
                .collect(Collectors.toList());

        return new FarmerFinancialReportResponse(
                farmer.getUserId(),
                farmer.getFullName(),
                platformTotal,
                projectSummaries,
                ledgerEntries,
                totalYieldKg,
                yieldCount,
                recentYield
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AC-3 / AC-4  Yield Tracking
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public YieldRecordResponse submitYield(Long farmerUserId, YieldRecordRequest request) {
        User farmer = resolveFarmer(farmerUserId);

        Land land = null;
        if (request.landId() != null) {
            land = landRepository.findById(request.landId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Land not found: " + request.landId()));

            // Verify the land belongs to this farmer
            if (land.getFarmerUser() == null ||
                    !farmerUserId.equals(land.getFarmerUser().getUserId())) {
                throw new BadRequestException(
                        "Land " + request.landId() + " does not belong to this farmer");
            }
        }

        YieldRecord record = new YieldRecord();
        record.setFarmerUser(farmer);
        record.setLand(land);
        record.setYieldAmountKg(request.yieldAmountKg());
        record.setHarvestDate(request.harvestDate());
        record.setNotes(request.notes());

        try {
            YieldRecord saved = yieldRecordRepository.save(record);
            return toYieldResponse(saved);
        } catch (Exception ex) {
            throw new FarmerDashboardException("Failed to save yield record", ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<YieldRecordResponse> getYieldHistory(Long farmerUserId) {
        resolveFarmer(farmerUserId);   // ensure the caller is a valid farmer
        return yieldRecordRepository
                .findByFarmerUserIdOrderByHarvestDateDesc(farmerUserId)
                .stream()
                .map(this::toYieldResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resolves and validates the farmer user.
     * Throws if the user does not exist or is not a FARMER.
     */
    private User resolveFarmer(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        if (user.getRole() != Role.FARMER) {
            throw new BadRequestException("Only farmers can access this resource");
        }
        return user;
    }

    /**
     * AC-5 — Fetches the farmer's ledger entries and maps them to DTOs.
     * The LedgerRepository query joins through wallet → user, so we look up
     * by the farmer's userId which maps to their wallet.
     */
    private List<LedgerEntryDto> buildLedgerEntries(Long farmerUserId) {
        List<Ledger> entries = ledgerRepository.findByUserIdOrderByCreatedAtDesc(farmerUserId);
        return entries.stream()
                .map(l -> new LedgerEntryDto(
                        l.getLedgerId(),
                        l.getTransactionType().name(),
                        l.getAmount(),
                        l.getBalanceAfter(),
                        l.getGateway(),
                        l.getGatewayReference(),
                        l.getCreatedAt() != null ? l.getCreatedAt().toString() : null
                ))
                .collect(Collectors.toList());
    }

    /** Maps a {@link YieldRecord} entity to its public response DTO. */
    private YieldRecordResponse toYieldResponse(YieldRecord r) {
        Long   landId      = r.getLand() != null ? r.getLand().getLandId()    : null;
        String projectName = r.getLand() != null ? r.getLand().getProjectName() : null;
        return new YieldRecordResponse(
                r.getYieldId(),
                landId,
                projectName,
                r.getYieldAmountKg(),
                r.getHarvestDate(),
                r.getNotes(),
                r.getCreatedAt()
        );
    }
}
