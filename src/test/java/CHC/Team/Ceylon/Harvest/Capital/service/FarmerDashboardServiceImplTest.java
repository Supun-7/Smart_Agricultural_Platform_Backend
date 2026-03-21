package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.dto.FarmerDashboardResponse;
import CHC.Team.Ceylon.Harvest.Capital.dto.FundedLandDto;
import CHC.Team.Ceylon.Harvest.Capital.entity.Farmer;
import CHC.Team.Ceylon.Harvest.Capital.entity.FarmerApplication;
import CHC.Team.Ceylon.Harvest.Capital.entity.Project;
import CHC.Team.Ceylon.Harvest.Capital.exception.FarmerDashboardException;
import CHC.Team.Ceylon.Harvest.Capital.repository.FarmerApplicationRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.FarmerRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FarmerDashboardServiceImplTest {

    @Mock
    private ProjectRepository projectRepository;


    @Mock
    private FarmerRepository farmerRepository;

    @Mock
    private FarmerApplicationRepository farmerApplicationRepository;

    @InjectMocks
    private FarmerDashboardServiceImpl farmerDashboardService;

    private Farmer farmer;
    private FarmerApplication latestApplication;

    @BeforeEach
    void setUp() {
        farmer = new Farmer();
        farmer.setLandName("Green Valley Plot");
        farmer.setLandLocation("Kandy");

        latestApplication = new FarmerApplication();
        latestApplication.setFarmAddress("No. 10, River Road");
        latestApplication.setFarmLocation("Matale");
    }

    @Test
    void getFarmerDashboard_shouldReturnAccurateFundedLandsAndSummaryFromRepositories() {
        Long userId = 42L;
        Project firstProject = buildProject(100L, "Pepper Expansion", 120000.0, 65.0);
        Project secondProject = buildProject(101L, "Cinnamon Revival", 200000.0, 40.0);
        Project notFundedProject = buildProject(102L, "Unfunded Trial", 0.0, 10.0);

        given(projectRepository.findByFarmerUserUserIdOrderByIdAsc(userId))
                .willReturn(List.of(firstProject, secondProject, notFundedProject));
        given(farmerRepository.findByUserUserId(userId)).willReturn(Optional.of(farmer));
        given(farmerApplicationRepository.findTopByUserUserIdOrderBySubmittedAtDesc(userId))
                .willReturn(Optional.of(latestApplication));

        FarmerDashboardResponse response = farmerDashboardService.getFarmerDashboard(userId);

        assertEquals(2, response.summary().totalFundedLands());
        assertEquals(BigDecimal.valueOf(320000.0), response.summary().totalInvestmentAmount());
        assertEquals(2, response.fundedLands().size());

        FundedLandDto firstLand = response.fundedLands().get(0);
        assertEquals(100L, firstLand.projectId());
        assertEquals("Pepper Expansion", firstLand.projectName());
        assertEquals("Green Valley Plot", firstLand.landName());
        assertEquals("Kandy", firstLand.farmLocation());
        assertEquals(BigDecimal.valueOf(120000.0), firstLand.investmentAmount());
        assertEquals(BigDecimal.valueOf(65.0), firstLand.projectProgress());

        FundedLandDto secondLand = response.fundedLands().get(1);
        assertEquals(101L, secondLand.projectId());
        assertEquals(BigDecimal.valueOf(200000.0), secondLand.investmentAmount());
    }

    @Test
    void getFarmerDashboard_shouldFallbackToLatestApplicationWhenFarmerProfileDataIsMissing() {
        Long userId = 84L;
        Project project = buildProject(201L, "Tea Upgrade", 50000.0, 90.0);

        given(projectRepository.findByFarmerUserUserIdOrderByIdAsc(userId)).willReturn(List.of(project));
        given(farmerRepository.findByUserUserId(userId)).willReturn(Optional.empty());
        given(farmerApplicationRepository.findTopByUserUserIdOrderBySubmittedAtDesc(userId))
                .willReturn(Optional.of(latestApplication));

        FarmerDashboardResponse response = farmerDashboardService.getFarmerDashboard(userId);

        assertEquals("No. 10, River Road", response.fundedLands().get(0).landName());
        assertEquals("Matale", response.fundedLands().get(0).farmLocation());
    }

    @Test
    void getFarmerDashboard_shouldWrapUnexpectedFailuresWithUserFriendlyMessage() {
        Long userId = 7L;
        given(projectRepository.findByFarmerUserUserIdOrderByIdAsc(userId))
                .willThrow(new RuntimeException("database offline"));

        FarmerDashboardException exception = assertThrows(
                FarmerDashboardException.class,
                () -> farmerDashboardService.getFarmerDashboard(userId));

        assertEquals(
                "Unable to load farmer dashboard at the moment. Please try again later.",
                exception.getMessage());
        verify(projectRepository).findByFarmerUserUserIdOrderByIdAsc(userId);
    }

    private Project buildProject(Long id, String name, Double currentAmount, Double progress) {
        Project project = new Project();
        project.setId(id);
        project.setProjectName(name);
        project.setCurrentAmount(currentAmount);
        project.setProgress(progress);
        return project;
    }
}
