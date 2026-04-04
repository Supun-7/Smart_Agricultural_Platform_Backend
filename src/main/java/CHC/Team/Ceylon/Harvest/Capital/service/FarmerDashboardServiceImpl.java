package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.dto.farmer.LandRegistrationRequest;
import CHC.Team.Ceylon.Harvest.Capital.dto.farmer.LandResponse;
import CHC.Team.Ceylon.Harvest.Capital.entity.FarmerApplication;
import CHC.Team.Ceylon.Harvest.Capital.entity.Land;
import CHC.Team.Ceylon.Harvest.Capital.entity.Project;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.exception.BadRequestException;
import CHC.Team.Ceylon.Harvest.Capital.exception.ConflictException;
import CHC.Team.Ceylon.Harvest.Capital.exception.ResourceNotFoundException;
import CHC.Team.Ceylon.Harvest.Capital.repository.FarmerApplicationRepository;
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

    private final UserRepository userRepository;
    private final FarmerApplicationRepository farmerApplicationRepository;
    private final ProjectRepository projectRepository;
    private final MilestoneService milestoneService;
    private final LandRepository landRepository;

    public FarmerDashboardServiceImpl(
            UserRepository userRepository,
            FarmerApplicationRepository farmerApplicationRepository,
            ProjectRepository projectRepository,
            MilestoneService milestoneService,
            LandRepository landRepository) {
        this.userRepository = userRepository;
        this.farmerApplicationRepository = farmerApplicationRepository;
        this.projectRepository = projectRepository;
        this.milestoneService = milestoneService;
        this.landRepository = landRepository;
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
            applicationInfo.put("status", app.getStatus().name());
            applicationInfo.put("farmerName", app.getFarmerName() != null ? app.getFarmerName() : "");
            applicationInfo.put("surname", app.getSurname() != null ? app.getSurname() : "");
            applicationInfo.put("farmLocation", app.getFarmLocation() != null ? app.getFarmLocation() : "");
            applicationInfo.put("cropTypes", app.getCropTypes() != null ? app.getCropTypes() : "");
            applicationInfo.put("farmAddress", app.getFarmAddress() != null ? app.getFarmAddress() : "");
            applicationInfo.put("nicNumber", app.getNicNumber() != null ? app.getNicNumber() : "");
            applicationInfo.put("submittedAt", app.getSubmittedAt() != null ? app.getSubmittedAt().toString() : "");
            applicationInfo.put("nicFrontUrl", app.getNicFrontUrl() != null ? app.getNicFrontUrl() : "");
            applicationInfo.put("nicBackUrl", app.getNicBackUrl() != null ? app.getNicBackUrl() : "");
            applicationInfo.put("landPhotoUrls", app.getLandPhotoUrls() != null ? app.getLandPhotoUrls() : "");
            applicationInfo.put("landSizeAcres", app.getLandSizeAcres() != null ? app.getLandSizeAcres() : 0);
        } else {
            applicationInfo.put("status", "NOT_SUBMITTED");
        }

        List<Project> projects = projectRepository
                .findByFarmerUserUserIdOrderByIdAsc(userId);

        List<Map<String, Object>> projectList = new ArrayList<>();
        double totalFunded = 0;

        for (Project p : projects) {
            Map<String, Object> proj = new HashMap<>();
            proj.put("id", p.getId());
            proj.put("projectName", p.getProjectName() != null ? p.getProjectName() : "");
            proj.put("currentAmount", p.getCurrentAmount() != null ? p.getCurrentAmount() : 0);
            proj.put("targetAmount", p.getTargetAmount() != null ? p.getTargetAmount() : 0);
            proj.put("progress", p.getProgress() != null ? p.getProgress() : 0);
            projectList.add(proj);
            if (p.getCurrentAmount() != null) {
                totalFunded += p.getCurrentAmount();
            }
        }

        List<LandResponse> lands = getFarmerLands(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("farmerId", user.getUserId());
        result.put("farmerName", user.getFullName());
        result.put("email", user.getEmail());
        result.put("status", user.getVerificationStatus().name());
        result.put("application", applicationInfo);
        result.put("projects", projectList);
        result.put("lands", lands);
        result.put("milestones", milestoneService.getFarmerMilestones(userId));
        result.put("totalProjects", projectList.size());
        result.put("totalFunded", totalFunded);
        result.put("activeLandCount", lands.stream().filter(LandResponse::isActive).count());
        result.put("landCount", lands.size());

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
                        request.location().trim()
                );
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

    private User getFarmerUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (user.getRole() != Role.FARMER) {
            throw new BadRequestException("Only farmers can manage land listings");
        }
        return user;
    }

    private LandResponse toResponse(Land land) {
        Long farmerId = null;
        String farmerName = null;
        if (land.getFarmerUser() != null) {
            farmerId = land.getFarmerUser().getUserId();
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
