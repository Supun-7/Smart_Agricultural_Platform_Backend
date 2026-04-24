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
import CHC.Team.Ceylon.Harvest.Capital.service.InvestorRoiService;
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

    private static final String POLYGON_SCAN_BASE = "https://amoy.polygonscan.com/tx/";

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final InvestmentRepository investmentRepository;
    private final KycSubmissionRepository kycSubmissionRepository;
    private final LandRepository landRepository;
    private final MilestoneService milestoneService;
    private final InvestorRoiService investorRoiService;

    public InvestorDashboardServiceImpl(
            UserRepository userRepository,
            WalletRepository walletRepository,
            InvestmentRepository investmentRepository,
            KycSubmissionRepository kycSubmissionRepository,
            LandRepository landRepository,
            MilestoneService milestoneService,
            InvestorRoiService investorRoiService) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.investmentRepository = investmentRepository;
        this.kycSubmissionRepository = kycSubmissionRepository;
        this.landRepository = landRepository;
        this.milestoneService = milestoneService;
        this.investorRoiService = investorRoiService;
    }

    @Override
    public Map<String, Object> getDashboard(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Investor not found: " + userId));

        Wallet wallet = walletRepository.findByUserUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found for investor: " + userId));

        String kycStatus = kycSubmissionRepository
                .findTopByUserUserIdOrderBySubmittedAtDesc(userId)
                .map(k -> k.getStatus().name())
                .orElse("NOT_SUBMITTED");

        List<Investment> investments = investmentRepository.findAllByUserIdWithLand(userId);

        List<Map<String, Object>> investedLands = new ArrayList<>();
        for (Investment inv : investments) {
            investedLands.add(toInvestedLandMap(inv));
        }

        Map<String, Object> breakdown = buildBreakdown(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("investorId",           user.getUserId());
        result.put("investorName",         user.getFullName());
        result.put("email",                user.getEmail());
        result.put("kycStatus",            kycStatus);
        result.put("walletBalance",        wallet.getBalance());
        result.put("currency",             wallet.getCurrency());
        result.put("investedLands",        investedLands);
        result.put("investmentBreakdown",  breakdown);
        result.put("approvedMilestones",   milestoneService.getApprovedMilestonesForProjects(
                investments.stream()
                        .map(inv -> inv.getLand().getProjectName())
                        .distinct()
                        .toList()
        ));
        result.put("portfolioRoiSummary", investorRoiService.buildPortfolioRoiSummary(investments));
        return result;
    }

    @Override
    public Map<String, Object> getOpportunities() {
        List<Land> activeLands = landRepository.findByIsActiveTrueOrderByCreatedAtDesc();

        List<Map<String, Object>> opportunities = new ArrayList<>();
        for (Land land : activeLands) {
            Map<String, Object> item = new HashMap<>();
            item.put("landId",             land.getLandId());
            item.put("projectName",        land.getProjectName());
            item.put("location",           land.getLocation());
            item.put("totalValue",         land.getTotalValue());
            item.put("minimumInvestment",  land.getMinimumInvestment());
            item.put("progressPercentage", land.getProgressPercentage());
            item.put("sizeAcres",          land.getSizeAcres());
            item.put("cropType",           land.getCropType());
            item.put("description",        land.getDescription());
            item.put("imageUrls",          land.getImageUrls());
            item.put("farmerName",         land.getFarmerUser() != null ? land.getFarmerUser().getFullName() : null);
            opportunities.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("opportunities", opportunities);
        result.put("total",         opportunities.size());
        return result;
    }

    @Override
    public Map<String, Object> getPortfolio(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Investor not found: " + userId));

        List<Investment> investments = investmentRepository.findAllByUserIdWithLand(userId);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Investment inv : investments) {
            items.add(toInvestedLandMap(inv));
        }

        BigDecimal totalInvested  = investmentRepository.sumTotalByUserId(userId);
        BigDecimal activeAmount   = investmentRepository.sumByUserIdAndStatus(userId, InvestmentStatus.ACTIVE);

        Map<String, Object> result = new HashMap<>();
        result.put("investments",    items);
        result.put("totalInvested",  totalInvested);
        result.put("activeAmount",   activeAmount);
        result.put("count",          items.size());
        result.put("portfolioRoiSummary", investorRoiService.buildPortfolioRoiSummary(investments));
        return result;
    }

    @Override
    public Map<String, Object> getReports(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Investor not found: " + userId));

        Wallet wallet = walletRepository.findByUserUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found for investor: " + userId));

        BigDecimal totalInvested    = investmentRepository.sumTotalByUserId(userId);
        BigDecimal activeAmount     = investmentRepository.sumByUserIdAndStatus(userId, InvestmentStatus.ACTIVE);
        BigDecimal completedAmount  = investmentRepository.sumByUserIdAndStatus(userId, InvestmentStatus.COMPLETED);
        BigDecimal pendingAmount    = investmentRepository.sumByUserIdAndStatus(userId, InvestmentStatus.PENDING);
        Long       totalLands       = investmentRepository.countDistinctLandsByUserId(userId);
        Long       activeLands      = investmentRepository.countActiveLandsByUserId(userId);

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalInvested",      totalInvested);
        summary.put("activeInvestments",  activeAmount);
        summary.put("completedReturns",   completedAmount);
        summary.put("pendingInvestments", pendingAmount);
        summary.put("walletBalance",      wallet.getBalance());
        summary.put("currency",           wallet.getCurrency());
        summary.put("totalLands",         totalLands);
        summary.put("activeLands",        activeLands);

        Map<String, Object> result = new HashMap<>();
        result.put("summary", summary);
        return result;
    }

    // ── Private helpers ────────────────────────────────────────────────────

    /**
     * Maps an investment to a response map including the blockchain tx link.
     * The polygonScanUrl field allows both investors and farmers to click through
     * to see the on-chain record on Polygon Amoy.
     */
    private Map<String, Object> toInvestedLandMap(Investment inv) {
        Land land = inv.getLand();
        Map<String, Object> map = new HashMap<>();
        map.put("investmentId",        inv.getInvestmentId());
        map.put("landId",              land.getLandId());
        map.put("projectName",         land.getProjectName());
        map.put("location",            land.getLocation());
        map.put("amountInvested",      inv.getAmountInvested());
        map.put("landTotalValue",      land.getTotalValue());
        map.put("progressPercentage",  land.getProgressPercentage());
        map.put("investmentDate",      inv.getInvestmentDate().toString());
        map.put("status",              inv.getStatus().name());

        // ── Blockchain link ───────────────────────────────────────────────────
        // Both the investor and farmer can use this URL to verify the contract on-chain.
        String txHash = inv.getBlockchainTxHash();
        map.put("blockchainTxHash", txHash);
        map.put("contractAddress",  inv.getContractAddress());
        map.put("polygonScanUrl",   buildPolygonScanUrl(txHash));
        map.putAll(investorRoiService.buildInvestmentRoiMetrics(inv));

        return map;
    }

    /**
     * Builds the PolygonScan link for a given tx hash.
     * Returns null if the hash is absent, a MOCK hash, or an error placeholder.
     */
    private String buildPolygonScanUrl(String txHash) {
        if (txHash == null || txHash.isBlank()
                || txHash.startsWith("BLOCKCHAIN_ERROR")
                || txHash.startsWith("PENDING")) {
            return null;
        }
        // MOCK hashes are longer than 66 chars (two UUIDs joined) — skip them
        if (txHash.length() > 66) {
            return null;
        }
        return POLYGON_SCAN_BASE + txHash;
    }

    @Override
    public Map<String, Object> getContracts(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Investor not found: " + userId));

        List<Investment> investments = investmentRepository.findAllByUserIdWithLand(userId);

        List<Map<String, Object>> contracts = new ArrayList<>();
        for (Investment inv : investments) {
            Land land = inv.getLand();
            Map<String, Object> c = new HashMap<>();
            c.put("investmentId",    inv.getInvestmentId());
            c.put("landId",          land.getLandId());
            c.put("projectName",     land.getProjectName());
            c.put("location",        land.getLocation());
            c.put("cropType",        land.getCropType());
            c.put("sizeAcres",       land.getSizeAcres());
            c.put("farmerName",      land.getFarmerUser() != null ? land.getFarmerUser().getFullName() : "—");
            c.put("amountInvested",  inv.getAmountInvested());
            c.put("investmentDate",  inv.getInvestmentDate() != null ? inv.getInvestmentDate().toString() : null);
            c.put("status",          inv.getStatus().name());

            // Blockchain fields
            String txHash = inv.getBlockchainTxHash();
            c.put("blockchainTxHash", txHash);
            c.put("contractAddress",  inv.getContractAddress());
            c.put("polygonScanUrl",   buildPolygonScanUrl(txHash));
            c.put("network",          (txHash != null && txHash.length() <= 66
                                       && !txHash.startsWith("BLOCKCHAIN_ERROR")
                                       && !txHash.startsWith("PENDING"))
                                      ? "POLYGON_AMOY" : "MOCK");
            contracts.add(c);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("contracts", contracts);
        result.put("total",     contracts.size());
        return result;
    }

    private Map<String, Object> buildBreakdown(Long userId) {
        Map<String, Object> map = new HashMap<>();
        map.put("totalInvested",         investmentRepository.sumTotalByUserId(userId));
        map.put("activeInvestments",     investmentRepository.sumByUserIdAndStatus(userId, InvestmentStatus.ACTIVE));
        map.put("pendingInvestments",    investmentRepository.sumByUserIdAndStatus(userId, InvestmentStatus.PENDING));
        map.put("completedInvestments",  investmentRepository.sumByUserIdAndStatus(userId, InvestmentStatus.COMPLETED));
        map.put("totalLandCount",        investmentRepository.countDistinctLandsByUserId(userId));
        map.put("activeLandCount",       investmentRepository.countActiveLandsByUserId(userId));
        return map;
    }
}
