package CHC.Team.Ceylon.Harvest.Capital.service.impl;

import CHC.Team.Ceylon.Harvest.Capital.entity.Investment;
import CHC.Team.Ceylon.Harvest.Capital.entity.Land;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.entity.Wallet;
import CHC.Team.Ceylon.Harvest.Capital.entity.Investment.InvestmentStatus;
import CHC.Team.Ceylon.Harvest.Capital.exception.ResourceNotFoundException;
import CHC.Team.Ceylon.Harvest.Capital.repository.InvestmentRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.KycSubmissionRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.LandRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.WalletRepository;
import CHC.Team.Ceylon.Harvest.Capital.service.InvestorDashboardService;
import CHC.Team.Ceylon.Harvest.Capital.service.MilestoneService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class InvestorDashboardServiceImpl implements InvestorDashboardService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final InvestmentRepository investmentRepository;
    private final KycSubmissionRepository kycSubmissionRepository;
    private final LandRepository landRepository;
    private final MilestoneService milestoneService;

    public InvestorDashboardServiceImpl(
            UserRepository userRepository,
            WalletRepository walletRepository,
            InvestmentRepository investmentRepository,
            KycSubmissionRepository kycSubmissionRepository,
            LandRepository landRepository,
            MilestoneService milestoneService) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.investmentRepository = investmentRepository;
        this.kycSubmissionRepository = kycSubmissionRepository;
        this.landRepository = landRepository;
        this.milestoneService = milestoneService;
    }

    // ── AC-1: Dashboard ────────────────────────────────────────────────────
    @Override
    public Map<String, Object> getDashboard(Long userId) {

        // resolve investor — throws 404 if missing
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investor not found: " + userId));

        // AC-3: wallet balance direct from DB — no hardcoded value
        Wallet wallet = walletRepository.findByUserUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Wallet not found for investor: " + userId));

        // latest KYC status
        String kycStatus = kycSubmissionRepository
                .findTopByUserUserIdOrderBySubmittedAtDesc(userId)
                .map(k -> k.getStatus().name())
                .orElse("NOT_SUBMITTED");

        // AC-2: all invested lands with amounts from DB
        List<Investment> investments = investmentRepository.findAllByUserIdWithLand(userId);

        List<Map<String, Object>> investedLands = new ArrayList<>();
        for (Investment inv : investments) {
            investedLands.add(toInvestedLandMap(inv));
        }

        // AC-4: breakdown from DB aggregates
        Map<String, Object> breakdown = buildBreakdown(userId);

        // AC-6: every field comes from DB — no hardcoded / mock data
        Map<String, Object> result = new HashMap<>();
        result.put("investorId", user.getUserId());
        result.put("investorName", user.getFullName());
        result.put("email", user.getEmail());
        result.put("kycStatus", kycStatus);
        result.put("walletBalance", wallet.getBalance());
        result.put("currency", wallet.getCurrency());
        result.put("investedLands", investedLands);
        result.put("investmentBreakdown", breakdown);
        result.put("approvedMilestones", milestoneService.getApprovedMilestonesForProjects(
                investments.stream()
                        .map(inv -> inv.getLand().getProjectName())
                        .distinct()
                        .toList()
        ));
        return result;
    }

    // ── Opportunities: real active lands from DB ───────────────────────────
    @Override
    public Map<String, Object> getOpportunities() {
        List<Land> activeLands = landRepository.findByIsActiveTrueOrderByCreatedAtDesc();

        List<Map<String, Object>> opportunities = new ArrayList<>();
        for (Land land : activeLands) {
            Map<String, Object> item = new HashMap<>();
            item.put("landId", land.getLandId());
            item.put("projectName", land.getProjectName());
            item.put("location", land.getLocation());
            item.put("totalValue", land.getTotalValue());
            item.put("minimumInvestment", land.getMinimumInvestment());
            item.put("progressPercentage", land.getProgressPercentage());
            item.put("sizeAcres", land.getSizeAcres());
            item.put("cropType", land.getCropType());
            item.put("description", land.getDescription());
            item.put("imageUrls", land.getImageUrls());
            item.put("farmerName", land.getFarmerUser() != null ? land.getFarmerUser().getFullName() : null);
            opportunities.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("opportunities", opportunities);
        result.put("total", opportunities.size());
        return result;
    }

    // ── Portfolio: investor's investments from DB ──────────────────────────
    @Override
    public Map<String, Object> getPortfolio(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investor not found: " + userId));

        List<Investment> investments = investmentRepository.findAllByUserIdWithLand(userId);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Investment inv : investments) {
            items.add(toInvestedLandMap(inv));
        }

        BigDecimal totalInvested = investmentRepository.sumTotalByUserId(userId);
        BigDecimal activeAmount = investmentRepository
                .sumByUserIdAndStatus(userId, InvestmentStatus.ACTIVE);

        Map<String, Object> result = new HashMap<>();
        result.put("investments", items);
        result.put("totalInvested", totalInvested);
        result.put("activeAmount", activeAmount);
        result.put("count", items.size());
        return result;
    }

    // ── Reports: financial summary from DB aggregates ─────────────────────
    @Override
    public Map<String, Object> getReports(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investor not found: " + userId));

        Wallet wallet = walletRepository.findByUserUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Wallet not found for investor: " + userId));

        BigDecimal totalInvested = investmentRepository.sumTotalByUserId(userId);
        BigDecimal activeAmount = investmentRepository
                .sumByUserIdAndStatus(userId, InvestmentStatus.ACTIVE);
        BigDecimal completedAmount = investmentRepository
                .sumByUserIdAndStatus(userId, InvestmentStatus.COMPLETED);
        BigDecimal pendingAmount = investmentRepository
                .sumByUserIdAndStatus(userId, InvestmentStatus.PENDING);
        Long totalLands = investmentRepository.countDistinctLandsByUserId(userId);
        Long activeLands = investmentRepository.countActiveLandsByUserId(userId);

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalInvested", totalInvested);
        summary.put("activeInvestments", activeAmount);
        summary.put("completedReturns", completedAmount);
        summary.put("pendingInvestments", pendingAmount);
        summary.put("walletBalance", wallet.getBalance());
        summary.put("currency", wallet.getCurrency());
        summary.put("totalLands", totalLands);
        summary.put("activeLands", activeLands);

        Map<String, Object> result = new HashMap<>();
        result.put("summary", summary);
        return result;
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private Map<String, Object> toInvestedLandMap(Investment inv) {
        Land land = inv.getLand();
        Map<String, Object> map = new HashMap<>();
        map.put("investmentId", inv.getInvestmentId());
        map.put("landId", land.getLandId());
        map.put("projectName", land.getProjectName());
        map.put("location", land.getLocation());
        map.put("amountInvested", inv.getAmountInvested());
        map.put("landTotalValue", land.getTotalValue());
        map.put("progressPercentage", land.getProgressPercentage());
        map.put("investmentDate", inv.getInvestmentDate().toString());
        map.put("status", inv.getStatus().name());
        return map;
    }

    private Map<String, Object> buildBreakdown(Long userId) {
        Map<String, Object> map = new HashMap<>();
        map.put("totalInvested", investmentRepository.sumTotalByUserId(userId));
        map.put("activeInvestments",
                investmentRepository.sumByUserIdAndStatus(userId, InvestmentStatus.ACTIVE));
        map.put("pendingInvestments",
                investmentRepository.sumByUserIdAndStatus(userId, InvestmentStatus.PENDING));
        map.put("completedInvestments",
                investmentRepository.sumByUserIdAndStatus(userId, InvestmentStatus.COMPLETED));
        map.put("totalLandCount", investmentRepository.countDistinctLandsByUserId(userId));
        map.put("activeLandCount", investmentRepository.countActiveLandsByUserId(userId));
        return map;
    }
}
