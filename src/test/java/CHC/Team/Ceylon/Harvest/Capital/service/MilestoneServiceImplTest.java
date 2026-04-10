package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.dto.MilestoneDetailResponse;
import CHC.Team.Ceylon.Harvest.Capital.dto.MilestoneSummaryResponse;
import CHC.Team.Ceylon.Harvest.Capital.entity.Land;
import CHC.Team.Ceylon.Harvest.Capital.entity.Milestone;
import CHC.Team.Ceylon.Harvest.Capital.entity.Project;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.MilestoneStatus;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import CHC.Team.Ceylon.Harvest.Capital.exception.BadRequestException;
import CHC.Team.Ceylon.Harvest.Capital.exception.ConflictException;
import CHC.Team.Ceylon.Harvest.Capital.exception.ResourceNotFoundException;
import CHC.Team.Ceylon.Harvest.Capital.repository.InvestmentRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.LandRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.MilestoneRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.ProjectRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;

import CHC.Team.Ceylon.Harvest.Capital.service.blockchain.BlockchainService;
import CHC.Team.Ceylon.Harvest.Capital.service.impl.MilestoneServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// AC-1: only pending milestones are returned for the auditor dashboard list
// AC-2: summary data includes farmer name, project name, progress %, notes, and date
// AC-3: detail view includes uploaded evidence files
// AC-4: approve changes status to APPROVED and syncs project/land progress
// AC-5: reject requires a non-blank rejection reason
// AC-6: rejected milestone stores the rejection reason for the farmer to view later
// AC-7: approved milestone can be retrieved for farmer/investor-facing views
// AC-8: already actioned milestones cannot be approved or rejected again
@ExtendWith(MockitoExtension.class)
class MilestoneServiceImplTest {

    @Mock
    private MilestoneRepository milestoneRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private LandRepository landRepository;

    @Mock
    private InvestmentRepository investmentRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private BlockchainService blockchainService;

    private MilestoneServiceImpl milestoneService;

    @BeforeEach
    void setUp() {
        milestoneService = new MilestoneServiceImpl(
                milestoneRepository,
                userRepository,
                projectRepository,
                landRepository,
                investmentRepository,
                new ObjectMapper(),
                auditLogService,
                blockchainService
        );
    }

    @Test
    void getPendingMilestones_shouldReturnMappedSummaryRows() {
        User farmer = buildUser(10L, "Nuwan Perera", "nuwan@farm.lk", Role.FARMER);
        Milestone milestone = buildMilestone(101L, farmer, MilestoneStatus.PENDING, 45, "Irrigation completed");
        Project project = buildProject(501L, "Kandy Tea Estate", farmer, 10.0);

        when(milestoneRepository.findAllByStatusWithRelations(MilestoneStatus.PENDING))
                .thenReturn(List.of(milestone));
        when(projectRepository.findByFarmerUserUserIdOrderByIdAsc(10L))
                .thenReturn(List.of(project));

        List<MilestoneSummaryResponse> result = milestoneService.getPendingMilestones();

        assertEquals(1, result.size());
        assertEquals("Nuwan Perera", result.get(0).farmerName());
        assertEquals("Kandy Tea Estate", result.get(0).projectName());
        assertEquals(45, result.get(0).progressPercentage());
        assertEquals("Irrigation completed", result.get(0).notes());
        assertEquals(LocalDate.of(2026, 3, 18), result.get(0).milestoneDate());
        assertEquals(MilestoneStatus.PENDING, result.get(0).status());
    }

    @Test
    void getMilestoneDetail_shouldReturnEvidenceFilesAndProjectData() {
        User farmer = buildUser(10L, "Nuwan Perera", "nuwan@farm.lk", Role.FARMER);
        User auditor = buildUser(88L, "Ayesha Auditor", "auditor@chc.lk", Role.AUDITOR);
        Milestone milestone = buildMilestone(101L, farmer, MilestoneStatus.REJECTED, 45, "Irrigation completed");
        milestone.setReviewedBy(auditor);
        milestone.setReviewedAt(LocalDateTime.of(2026, 3, 25, 11, 0));
        milestone.setRejectionReason("Evidence is blurry");
        milestone.setEvidenceFilesJson("[\"https://cdn.test/photo-1.jpg\",\"https://cdn.test/report.pdf\"]");

        Project project = buildProject(501L, "Kandy Tea Estate", farmer, 10.0);

        when(milestoneRepository.findByIdWithRelations(101L)).thenReturn(Optional.of(milestone));
        when(projectRepository.findByFarmerUserUserIdOrderByIdAsc(10L)).thenReturn(List.of(project));

        MilestoneDetailResponse result = milestoneService.getMilestoneDetail(101L);

        assertEquals("Nuwan Perera", result.farmerName());
        assertEquals("Kandy Tea Estate", result.projectName());
        assertEquals(MilestoneStatus.REJECTED, result.status());
        assertEquals("Evidence is blurry", result.rejectionReason());
        assertEquals(2, result.evidenceFiles().size());
        assertEquals("photo-1.jpg", result.evidenceFiles().get(0).name());
        assertEquals("https://cdn.test/report.pdf", result.evidenceFiles().get(1).url());
    }

    @Test
    void approveMilestone_shouldSetApprovedStatusAndSyncProgress() {
        User farmer = buildUser(10L, "Nuwan Perera", "nuwan@farm.lk", Role.FARMER);
        User auditor = buildUser(88L, "Ayesha Auditor", "auditor@chc.lk", Role.AUDITOR);
        Milestone milestone = buildMilestone(101L, farmer, MilestoneStatus.PENDING, 70, "Field inspection passed");
        Project project = buildProject(501L, "Kandy Tea Estate", farmer, 10.0);
        Land land = buildLand(900L, "Kandy Tea Estate", 10);

        when(milestoneRepository.findByIdWithRelations(101L)).thenReturn(Optional.of(milestone));
        when(userRepository.findById(88L)).thenReturn(Optional.of(auditor));
        when(projectRepository.findByFarmerUserUserIdOrderByIdAsc(10L)).thenReturn(List.of(project));
        when(landRepository.findByProjectNameIgnoreCase("Kandy Tea Estate")).thenReturn(List.of(land));
        when(milestoneRepository.save(any(Milestone.class))).thenReturn(milestone);
        when(projectRepository.save(any(Project.class))).thenReturn(project);

        MilestoneDetailResponse result = milestoneService.approveMilestone(101L, 88L);

        assertEquals(MilestoneStatus.APPROVED, milestone.getStatus());
        assertEquals("Ayesha Auditor", result.reviewedBy());
        assertEquals("Kandy Tea Estate", result.projectName());
        assertEquals(70.0, project.getProgress());
        assertEquals(70, land.getProgressPercentage());

        verify(projectRepository).save(project);
        verify(landRepository).saveAll(anyList());
        verify(milestoneRepository).save(milestone);
    }

    @Test
    void rejectMilestone_shouldRequireNonBlankReason() {
        User farmer = buildUser(10L, "Nuwan Perera", "nuwan@farm.lk", Role.FARMER);
        Milestone milestone = buildMilestone(101L, farmer, MilestoneStatus.PENDING, 45, "Irrigation completed");

        when(milestoneRepository.findByIdWithRelations(101L)).thenReturn(Optional.of(milestone));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> milestoneService.rejectMilestone(101L, 88L, "   "));

        assertEquals("Rejection reason is required", ex.getMessage());
        verify(userRepository, never()).findById(88L);
        verify(milestoneRepository, never()).save(any(Milestone.class));
    }

    @Test
    void rejectMilestone_shouldStoreRejectedStatusAndReason() {
        User farmer = buildUser(10L, "Nuwan Perera", "nuwan@farm.lk", Role.FARMER);
        User auditor = buildUser(88L, "Ayesha Auditor", "auditor@chc.lk", Role.AUDITOR);
        Milestone milestone = buildMilestone(101L, farmer, MilestoneStatus.PENDING, 45, "Irrigation completed");
        Project project = buildProject(501L, "Kandy Tea Estate", farmer, 10.0);

        when(milestoneRepository.findByIdWithRelations(101L)).thenReturn(Optional.of(milestone));
        when(userRepository.findById(88L)).thenReturn(Optional.of(auditor));
        when(projectRepository.findByFarmerUserUserIdOrderByIdAsc(10L)).thenReturn(List.of(project));
        when(milestoneRepository.save(any(Milestone.class))).thenReturn(milestone);

        MilestoneDetailResponse result = milestoneService.rejectMilestone(101L, 88L, "Missing field photo");

        assertEquals(MilestoneStatus.REJECTED, milestone.getStatus());
        assertEquals("Missing field photo", milestone.getRejectionReason());
        assertEquals("Missing field photo", result.rejectionReason());
    }

    @Test
    void approveMilestone_shouldRejectAlreadyActionedMilestones() {
        User farmer = buildUser(10L, "Nuwan Perera", "nuwan@farm.lk", Role.FARMER);
        Milestone milestone = buildMilestone(101L, farmer, MilestoneStatus.APPROVED, 45, "Already checked");

        when(milestoneRepository.findByIdWithRelations(101L)).thenReturn(Optional.of(milestone));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> milestoneService.approveMilestone(101L, 88L));

        assertEquals("Milestone has already been actioned", ex.getMessage());
        verify(userRepository, never()).findById(88L);
    }

    @Test
    void getApprovedMilestonesForProjects_shouldReturnInvestorFacingApprovedItemsOnly() {
        User farmer1 = buildUser(10L, "Nuwan Perera", "nuwan@farm.lk", Role.FARMER);
        User farmer2 = buildUser(11L, "Amali Silva", "amali@farm.lk", Role.FARMER);

        Milestone approvedTea = buildMilestone(101L, farmer1, MilestoneStatus.APPROVED, 80, "Tea harvest verified");
        Milestone approvedSpice = buildMilestone(102L, farmer2, MilestoneStatus.APPROVED, 65, "Spice beds prepared");

        Project teaProject = buildProject(501L, "Kandy Tea Estate", farmer1, 10.0);
        Project spiceProject = buildProject(502L, "Matale Spice Farm", farmer2, 20.0);

        when(milestoneRepository.findAllByStatusWithRelations(MilestoneStatus.APPROVED))
                .thenReturn(List.of(approvedTea, approvedSpice));
        when(projectRepository.findByFarmerUserUserIdOrderByIdAsc(10L)).thenReturn(List.of(teaProject));
        when(projectRepository.findByFarmerUserUserIdOrderByIdAsc(11L)).thenReturn(List.of(spiceProject));

        List<MilestoneDetailResponse> result = milestoneService.getApprovedMilestonesForProjects(
                List.of("Kandy Tea Estate")
        );

        assertEquals(1, result.size());
        assertEquals("Kandy Tea Estate", result.get(0).projectName());
        assertEquals(MilestoneStatus.APPROVED, result.get(0).status());
    }

    @Test
    void getPendingMilestones_shouldThrowWhenFarmerProjectMissing() {
        User farmer = buildUser(10L, "Nuwan Perera", "nuwan@farm.lk", Role.FARMER);
        Milestone milestone = buildMilestone(101L, farmer, MilestoneStatus.PENDING, 45, "Irrigation completed");

        when(milestoneRepository.findAllByStatusWithRelations(MilestoneStatus.PENDING))
                .thenReturn(List.of(milestone));
        when(projectRepository.findByFarmerUserUserIdOrderByIdAsc(10L)).thenReturn(List.of());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> milestoneService.getPendingMilestones());

        assertTrue(ex.getMessage().contains("Project not found for farmer user id: 10"));
    }

    private User buildUser(Long id, String fullName, String email, Role role) {
        User user = new User();
        user.setUserId(id);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setRole(role);
        user.setVerificationStatus(VerificationStatus.VERIFIED);
        user.setPasswordHash("hashed");
        return user;
    }

    private Milestone buildMilestone(Long id, User farmer, MilestoneStatus status, Integer progressPercentage, String notes) {
        Milestone milestone = new Milestone();
        milestone.setId(id);
        milestone.setFarmer(farmer);
        milestone.setStatus(status);
        milestone.setProgressPercentage(progressPercentage);
        milestone.setNotes(notes);
        milestone.setMilestoneDate(LocalDate.of(2026, 3, 18));
        milestone.setCreatedAt(LocalDateTime.of(2026, 3, 18, 9, 30));
        milestone.setUpdatedAt(LocalDateTime.of(2026, 3, 18, 9, 30));
        return milestone;
    }

    private Project buildProject(Long id, String name, User farmer, Double progress) {
        Project project = new Project();
        project.setId(id);
        project.setProjectName(name);
        project.setFarmerUser(farmer);
        project.setProgress(progress);
        project.setCurrentAmount(120000.0);
        project.setTargetAmount(250000.0);
        return project;
    }

    private Land buildLand(Long id, String projectName, Integer progressPercentage) {
        Land land = new Land();
        land.setLandId(id);
        land.setProjectName(projectName);
        land.setLocation("Kandy");
        land.setTotalValue(BigDecimal.valueOf(500000));
        land.setMinimumInvestment(BigDecimal.valueOf(5000));
        land.setProgressPercentage(progressPercentage);
        land.setIsActive(true);
        land.setCreatedAt(LocalDateTime.of(2026, 3, 1, 8, 0));
        return land;
    }
}
