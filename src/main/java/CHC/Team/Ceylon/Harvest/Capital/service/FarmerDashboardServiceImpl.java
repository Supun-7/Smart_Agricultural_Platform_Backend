package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.dto.farmer.LandRegistrationRequest;
import CHC.Team.Ceylon.Harvest.Capital.dto.farmer.LandResponse;
import CHC.Team.Ceylon.Harvest.Capital.entity.FarmerApplication;
import CHC.Team.Ceylon.Harvest.Capital.entity.Investment;
import CHC.Team.Ceylon.Harvest.Capital.entity.Land;
import CHC.Team.Ceylon.Harvest.Capital.entity.Project;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.exception.BadRequestException;
import CHC.Team.Ceylon.Harvest.Capital.exception.ConflictException;
import CHC.Team.Ceylon.Harvest.Capital.exception.ResourceNotFoundException;
import CHC.Team.Ceylon.Harvest.Capital.repository.FarmerApplicationRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.InvestmentRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.LandRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.ProjectRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class FarmerDashboardServiceImpl implements FarmerDashboardService {

    private static final String POLYGON_SCAN_BASE = "https://amoy.polygonscan.com/tx/";

    private final UserRepository              userRepository;
    private final FarmerApplicationRepository farmerApplicationRepository;
    private final ProjectRepository           projectRepository;
    private final MilestoneService            milestoneService;
    private final LandRepository              landRepository;
    private final InvestmentRepository        investmentRepository;

    public FarmerDashboardServiceImpl(
            UserRepository              userRepository,
            FarmerApplicationRepository farmerApplicationRepository,
            ProjectRepository           projectRepository,
            MilestoneService            milestoneService,
            LandRepository              landRepository,
            InvestmentRepository        investmentRepository) {
        this.userRepository              = userRepository;
        this.farmerApplicationRepository = farmerApplicationRepository;
        this.projectRepository           = projectRepository;
        this.milestoneService            = milestoneService;
        this.landRepository              = landRepository;
        this.investmentRepository        = investmentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getFarmerDashboard(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Farmer not found: " + userId));

        Optional<FarmerApplication> latestApp = farmerApplicationRepository
                .findTopByUserUserIdOrderBySubmittedAtDesc(userId);

        Map<String, Object> applicationInfo = new HashMap<>();
        if (latestApp.isPresent()) {
            FarmerApplication app = latestApp.get();
            applicationInfo.put("status",       app.getStatus().name());
            applicationInfo.put("farmerName",   app.getFarmerName()   != null ? app.getFarmerName()   : "");
            applicationInfo.put("surname",      app.getSurname()      != null ? app.getSurname()      : "");
            applicationInfo.put("farmLocation", app.getFarmLocation() != null ? app.getFarmLocation() : "");
            applicationInfo.put("cropTypes",    app.getCropTypes()    != null ? app.getCropTypes()    : "");
            applicationInfo.put("farmAddress",  app.getFarmAddress()  != null ? app.getFarmAddress()  : "");
            applicationInfo.put("nicNumber",    app.getNicNumber()    != null ? app.getNicNumber()    : "");
            applicationInfo.put("submittedAt",  app.getSubmittedAt()  != null ? app.getSubmittedAt().toString() : "");
            applicationInfo.put("nicFrontUrl",  app.getNicFrontUrl()  != null ? app.getNicFrontUrl()  : "");
            applicationInfo.put("nicBackUrl",   app.getNicBackUrl()   != null ? app.getNicBackUrl()   : "");
            applicationInfo.put("landPhotoUrls",app.getLandPhotoUrls()!= null ? app.getLandPhotoUrls(): "");
            applicationInfo.put("landSizeAcres",app.getLandSizeAcres()!= null ? app.getLandSizeAcres(): 0);
        } else {
            applicationInfo.put("status", "NOT_SUBMITTED");
        }

        List<Project> projects = projectRepository.findByFarmerUserUserIdOrderByIdAsc(userId);

        List<Map<String, Object>> projectList = new ArrayList<>();
        double totalFunded = 0;

        for (Project p : projects) {
            Map<String, Object> proj = new HashMap<>();
            proj.put("id",            p.getId());
            proj.put("projectName",   p.getProjectName()   != null ? p.getProjectName()   : "");
            proj.put("currentAmount", p.getCurrentAmount() != null ? p.getCurrentAmount() : 0);
            proj.put("targetAmount",  p.getTargetAmount()  != null ? p.getTargetAmount()  : 0);
            proj.put("progress",      p.getProgress()      != null ? p.getProgress()      : 0);
            projectList.add(proj);
            if (p.getCurrentAmount() != null) totalFunded += p.getCurrentAmount();
        }

        List<LandResponse> lands = getFarmerLands(userId);

        // ── Investments on this farmer's lands (with blockchain links) ────────
        // Each entry includes a polygonScanUrl so the farmer can verify on-chain
        // that investor payments are permanently recorded.
        List<Map<String, Object>> receivedInvestments = buildReceivedInvestments(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("farmerId",           user.getUserId());
        result.put("farmerName",         user.getFullName());
        result.put("email",              user.getEmail());
        result.put("status",             user.getVerificationStatus().name());
        result.put("application",        applicationInfo);
        result.put("projects",           projectList);
        result.put("lands",              lands);
        result.put("milestones",         milestoneService.getFarmerMilestones(userId));
        result.put("receivedInvestments", receivedInvestments);
        result.put("totalProjects",      projectList.size());
        result.put("totalFunded",        totalFunded);
        result.put("activeLandCount",    lands.stream().filter(LandResponse::isActive).count());
        result.put("landCount",          lands.size());

        return result;
    }

    @Override
    public LandResponse createLand(Long userId, LandRegistrationRequest request) {
        User farmer = getFarmerUser(userId);

        if (request.minimumInvestment().compareTo(request.totalValue()) > 0) {
            throw new BadRequestException("Minimum investment cannot be greater than total value");
        }

        boolean duplicate = landRepository
                .existsByFarmerUserUserIdAndProjectNameIgnoreCaseAndLocationIgnoreCaseAndIsActiveTrue(
                        userId,
                        request.projectName().trim(),
                        request.location().trim());
        if (duplicate) {
            throw new ConflictException("You already have an active land listing with the same name and location");
        }

        Land land = new Land();
        land.setFarmerUser(farmer);
        land.setProjectName(request.projectName().trim());
        land.setLocation(request.location().trim());
        land.setSizeAcres(request.sizeAcres());
        land.setCropType(request.cropType().trim());
        land.setDescription(request.description().trim());
        land.setImageUrls(request.imageUrls().trim());
        land.setTotalValue(request.totalValue());
        land.setMinimumInvestment(request.minimumInvestment());
        land.setProgressPercentage(0);
        land.setIsActive(true);

        return toResponse(landRepository.save(land));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LandResponse> getFarmerLands(Long userId) {
        getFarmerUser(userId);
        return landRepository.findByFarmerUserUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public LandResponse updateLandStatus(Long userId, Long landId, boolean isActive) {
        getFarmerUser(userId);
        Land land = landRepository.findById(landId)
                .orElseThrow(() -> new ResourceNotFoundException("Land not found: " + landId));

        if (land.getFarmerUser() == null || !userId.equals(land.getFarmerUser().getUserId())) {
            throw new ResourceNotFoundException("Land not found for the current farmer");
        }

        land.setIsActive(isActive);
        return toResponse(landRepository.save(land));
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Returns all investments on this farmer's lands, including the PolygonScan
     * link so the farmer can independently verify each investment on-chain.
     */
    private List<Map<String, Object>> buildReceivedInvestments(Long farmerUserId) {
        List<Investment> investments =
                investmentRepository.findAllByFarmerUserIdWithInvestor(farmerUserId);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Investment inv : investments) {
            Map<String, Object> map = new HashMap<>();
            map.put("investmentId",    inv.getInvestmentId());
            map.put("landId",          inv.getLand().getLandId());
            map.put("projectName",     inv.getLand().getProjectName());
            map.put("investorName",    inv.getInvestor().getFullName());
            map.put("amountInvested",  inv.getAmountInvested());
            map.put("investmentDate",  inv.getInvestmentDate().toString());
            map.put("status",          inv.getStatus().name());

            // Blockchain on-chain proof
            String txHash = inv.getBlockchainTxHash();
            map.put("blockchainTxHash", txHash);
            map.put("contractAddress",  inv.getContractAddress());
            map.put("polygonScanUrl",   buildPolygonScanUrl(txHash));

            result.add(map);
        }
        return result;
    }

    /**
     * Builds a PolygonScan URL for a real Amoy tx hash.
     * Returns null for mock/error/pending hashes.
     */
    private String buildPolygonScanUrl(String txHash) {
        if (txHash == null || txHash.isBlank()
                || txHash.startsWith("BLOCKCHAIN_ERROR")
                || txHash.startsWith("PENDING")) {
            return null;
        }
        // MOCK hashes are longer than 66 chars — skip them
        if (txHash.length() > 66) {
            return null;
        }
        return POLYGON_SCAN_BASE + txHash;
    }

    /**
     * Returns all investment contracts received on this farmer's lands.
     * Blockchain links are intentionally omitted — the frontend Sinhala UI
     * does not show them to farmers to keep the experience simple.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getFarmerContracts(Long userId) {
        getFarmerUser(userId);

        List<Investment> investments =
                investmentRepository.findAllByFarmerUserIdWithInvestor(userId);

        List<Map<String, Object>> contracts = new ArrayList<>();
        for (Investment inv : investments) {
            Map<String, Object> c = new HashMap<>();
            c.put("investmentId",   inv.getInvestmentId());
            c.put("projectName",    inv.getLand().getProjectName());
            c.put("location",       inv.getLand().getLocation());
            c.put("cropType",       inv.getLand().getCropType());
            c.put("sizeAcres",      inv.getLand().getSizeAcres());
            c.put("investorName",   inv.getInvestor().getFullName());
            c.put("amountInvested", inv.getAmountInvested());
            c.put("investmentDate", inv.getInvestmentDate() != null
                                    ? inv.getInvestmentDate().toString() : null);
            c.put("status",         inv.getStatus().name());
            // No blockchainTxHash or polygonScanUrl — not shown to farmers
            contracts.add(c);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("contracts", contracts);
        result.put("total",     contracts.size());
        return result;
    }

    private User getFarmerUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        if (user.getRole() != Role.FARMER) {
            throw new BadRequestException("Only farmers can manage land listings");
        }
        return user;
    }

    private LandResponse toResponse(Land land) {
        Long   farmerId   = null;
        String farmerName = null;
        if (land.getFarmerUser() != null) {
            farmerId   = land.getFarmerUser().getUserId();
            farmerName = land.getFarmerUser().getFullName();
        }
        return new LandResponse(
                land.getLandId(),
                land.getProjectName(),
                land.getLocation(),
                land.getSizeAcres(),
                land.getCropType(),
                land.getDescription(),
                land.getImageUrls(),
                land.getTotalValue(),
                land.getMinimumInvestment(),
                land.getProgressPercentage(),
                land.getIsActive(),
                land.getCreatedAt(),
                land.getUpdatedAt(),
                farmerId,
                farmerName
        );
    }
}
