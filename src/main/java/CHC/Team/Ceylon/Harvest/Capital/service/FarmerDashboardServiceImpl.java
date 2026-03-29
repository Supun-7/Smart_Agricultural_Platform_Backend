package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.entity.FarmerApplication;
import CHC.Team.Ceylon.Harvest.Capital.entity.Project;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.repository.FarmerApplicationRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.ProjectRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class FarmerDashboardServiceImpl implements FarmerDashboardService {

    private final UserRepository               userRepository;
    private final FarmerApplicationRepository  farmerApplicationRepository;
    private final ProjectRepository            projectRepository;
    private final MilestoneService             milestoneService;

    public FarmerDashboardServiceImpl(
            UserRepository userRepository,
            FarmerApplicationRepository farmerApplicationRepository,
            ProjectRepository projectRepository,
            MilestoneService milestoneService) {
        this.userRepository              = userRepository;
        this.farmerApplicationRepository = farmerApplicationRepository;
        this.projectRepository           = projectRepository;
        this.milestoneService            = milestoneService;
    }

    @Override
    public Map<String, Object> getFarmerDashboard(Long userId) {

        // ── Farmer user info ──────────────────────────────────
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Farmer not found: " + userId));

        // ── Latest farmer application ─────────────────────────
        Optional<FarmerApplication> latestApp = farmerApplicationRepository
                .findTopByUserUserIdOrderBySubmittedAtDesc(userId);

        Map<String, Object> applicationInfo = new HashMap<>();
        if (latestApp.isPresent()) {
            FarmerApplication app = latestApp.get();
            applicationInfo.put("status",        app.getStatus().name());
            applicationInfo.put("farmerName",    app.getFarmerName()   != null ? app.getFarmerName()   : "");
            applicationInfo.put("surname",       app.getSurname()      != null ? app.getSurname()      : "");
            applicationInfo.put("farmLocation",  app.getFarmLocation() != null ? app.getFarmLocation() : "");
            applicationInfo.put("cropTypes",     app.getCropTypes()    != null ? app.getCropTypes()    : "");
            applicationInfo.put("farmAddress",   app.getFarmAddress()  != null ? app.getFarmAddress()  : "");
            applicationInfo.put("nicNumber",     app.getNicNumber()    != null ? app.getNicNumber()    : "");
            applicationInfo.put("submittedAt",   app.getSubmittedAt()  != null ? app.getSubmittedAt().toString() : "");
            applicationInfo.put("nicFrontUrl",   app.getNicFrontUrl()  != null ? app.getNicFrontUrl()  : "");
            applicationInfo.put("nicBackUrl",    app.getNicBackUrl()   != null ? app.getNicBackUrl()   : "");
            applicationInfo.put("landPhotoUrls", app.getLandPhotoUrls() != null ? app.getLandPhotoUrls() : "");
            if (app.getLandSizeAcres() != null) {
                applicationInfo.put("landSizeAcres", app.getLandSizeAcres());
            } else {
                applicationInfo.put("landSizeAcres", 0);
            }
        } else {
            applicationInfo.put("status", "NOT_SUBMITTED");
        }

        // ── Projects for this farmer ──────────────────────────
        List<Project> projects = projectRepository
                .findByFarmerUserUserIdOrderByIdAsc(userId);

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
            if (p.getCurrentAmount() != null) {
                totalFunded += p.getCurrentAmount();
            }
        }

        // ── Build final response ──────────────────────────────
        Map<String, Object> result = new HashMap<>();
        result.put("farmerId",        user.getUserId());
        result.put("farmerName",      user.getFullName());
        result.put("email",           user.getEmail());
        result.put("status",          user.getVerificationStatus().name());
        result.put("application",     applicationInfo);
        result.put("projects",        projectList);
        result.put("milestones",      milestoneService.getFarmerMilestones(userId));
        result.put("totalProjects",   projectList.size());
        result.put("totalFunded",     totalFunded);

        return result;
    }
}