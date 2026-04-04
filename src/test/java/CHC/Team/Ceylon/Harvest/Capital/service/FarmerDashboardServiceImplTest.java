package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.entity.FarmerApplication;
import CHC.Team.Ceylon.Harvest.Capital.entity.Project;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import CHC.Team.Ceylon.Harvest.Capital.repository.FarmerApplicationRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.LandRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.ProjectRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FarmerDashboardServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private FarmerApplicationRepository farmerApplicationRepository;

    @Mock
    private MilestoneService milestoneService;

    @Mock
    private LandRepository landRepository;

    @InjectMocks
    private FarmerDashboardServiceImpl farmerDashboardService;

    private User farmer;
    private FarmerApplication latestApplication;

    @BeforeEach
    void setUp() {
        farmer = new User();
        farmer.setUserId(42L);
        farmer.setFullName("Test Farmer");
        farmer.setEmail("farmer@test.com");
        farmer.setRole(Role.FARMER);
        farmer.setVerificationStatus(VerificationStatus.VERIFIED);

        latestApplication = new FarmerApplication();
        latestApplication.setFarmAddress("No. 10, River Road");
        latestApplication.setFarmLocation("Matale");
        latestApplication.setStatus(VerificationStatus.VERIFIED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getFarmerDashboard_shouldReturnAccurateFundedLandsAndSummaryFromRepositories() {
        Long userId = 42L;
        Project firstProject  = buildProject(100L, "Pepper Expansion", 120000.0, 65.0);
        Project secondProject = buildProject(101L, "Cinnamon Revival",  200000.0, 40.0);
        Project unfundedProject = buildProject(102L, "Unfunded Trial",    0.0,     10.0);

        given(userRepository.findById(userId)).willReturn(Optional.of(farmer));
        given(projectRepository.findByFarmerUserUserIdOrderByIdAsc(userId))
                .willReturn(List.of(firstProject, secondProject, unfundedProject));
        given(farmerApplicationRepository.findTopByUserUserIdOrderBySubmittedAtDesc(userId))
                .willReturn(Optional.of(latestApplication));
        given(milestoneService.getFarmerMilestones(userId)).willReturn(List.of());

        Map<String, Object> response = farmerDashboardService.getFarmerDashboard(userId);

        assertNotNull(response);
        assertEquals(userId, response.get("farmerId"));
        assertEquals("Test Farmer", response.get("farmerName"));

        // Total funded = 120000 + 200000 + 0 = 320000
        assertEquals(320000.0, (Double) response.get("totalFunded"), 0.001);
        assertEquals(3, response.get("totalProjects"));

        List<Map<String, Object>> projects =
                (List<Map<String, Object>>) response.get("projects");
        assertNotNull(projects);
        assertEquals(3, projects.size());

        Map<String, Object> first = projects.get(0);
        assertEquals(100L, first.get("id"));
        assertEquals("Pepper Expansion", first.get("projectName"));
        assertEquals(120000.0, (Double) first.get("currentAmount"), 0.001);
        assertEquals(65.0,     (Double) first.get("progress"),      0.001);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getFarmerDashboard_shouldIncludeApplicationDataInResponse() {
        Long userId = 42L;

        given(userRepository.findById(userId)).willReturn(Optional.of(farmer));
        given(projectRepository.findByFarmerUserUserIdOrderByIdAsc(userId))
                .willReturn(List.of());
        given(farmerApplicationRepository.findTopByUserUserIdOrderBySubmittedAtDesc(userId))
                .willReturn(Optional.of(latestApplication));
        given(milestoneService.getFarmerMilestones(userId)).willReturn(List.of());

        Map<String, Object> response = farmerDashboardService.getFarmerDashboard(userId);

        Map<String, Object> application = (Map<String, Object>) response.get("application");
        assertNotNull(application);
        assertEquals("No. 10, River Road", application.get("farmAddress"));
        assertEquals("Matale",             application.get("farmLocation"));
        assertEquals("VERIFIED",           application.get("status"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getFarmerDashboard_whenNoApplicationExists_shouldSetStatusToNotSubmitted() {
        Long userId = 42L;

        given(userRepository.findById(userId)).willReturn(Optional.of(farmer));
        given(projectRepository.findByFarmerUserUserIdOrderByIdAsc(userId))
                .willReturn(List.of());
        given(farmerApplicationRepository.findTopByUserUserIdOrderBySubmittedAtDesc(userId))
                .willReturn(Optional.empty());
        given(milestoneService.getFarmerMilestones(userId)).willReturn(List.of());

        Map<String, Object> response = farmerDashboardService.getFarmerDashboard(userId);

        Map<String, Object> application = (Map<String, Object>) response.get("application");
        assertNotNull(application);
        assertEquals("NOT_SUBMITTED", application.get("status"));
    }

    @Test
    void getFarmerDashboard_whenUserNotFound_shouldThrowRuntimeException() {
        Long userId = 7L;
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> farmerDashboardService.getFarmerDashboard(userId));

        assertTrue(exception.getMessage().contains("7"),
                "Exception message should contain the missing userId");
        verify(userRepository).findById(userId);
    }

    // ── Helper ────────────────────────────────────────────────

    private Project buildProject(Long id, String name, Double currentAmount, Double progress) {
        Project project = new Project();
        project.setId(id);
        project.setProjectName(name);
        project.setCurrentAmount(currentAmount);
        project.setProgress(progress);
        return project;
    }
}
