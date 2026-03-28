package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.dto.MilestoneDto;
import CHC.Team.Ceylon.Harvest.Capital.dto.ProjectMilestoneResponseDto;
import CHC.Team.Ceylon.Harvest.Capital.entity.Investment;
import CHC.Team.Ceylon.Harvest.Capital.entity.Land;
import CHC.Team.Ceylon.Harvest.Capital.entity.Milestone;
import CHC.Team.Ceylon.Harvest.Capital.entity.Milestone.MilestoneStatus;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.exception.ResourceNotFoundException;
import CHC.Team.Ceylon.Harvest.Capital.repository.InvestmentRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.LandRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.MilestoneRepository;
import CHC.Team.Ceylon.Harvest.Capital.service.impl.InvestorMilestoneServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for InvestorMilestoneServiceImpl.
 *
 * Story: CHC-112 — Investor View Project Progress and Milestone Status
 *
 * AC-1: An investor can navigate to a project detail page from their dashboard.
 * AC-2: Only APPROVED milestones are visible to the investor.
 * AC-3: Each milestone displays progressPercentage, notes, date, and approvalStatus.
 * AC-4: A visual milestone timeline / progress bar is shown per project (latest first).
 * AC-5: PENDING and REJECTED milestones are not visible to the investor.
 */
@ExtendWith(MockitoExtension.class)
class InvestorMilestoneServiceImplTest {

    @Mock
    private LandRepository landRepository;

    @Mock
    private InvestmentRepository investmentRepository;

    @Mock
    private MilestoneRepository milestoneRepository;

    @InjectMocks
    private InvestorMilestoneServiceImpl milestoneService;

    // ── Shared test fixtures ─────────────────────────────────────────────────

    private User investor;
    private Land land;

    @BeforeEach
    void setUp() {
        investor = new User();
        investor.setUserId(10L);
        investor.setFullName("Sachith Investor");
        investor.setEmail("sachith@test.com");
        investor.setRole(Role.INVESTOR);

        land = new Land();
        land.setLandId(71L);
        land.setProjectName("Pepper Estate Phase 1");
        land.setLocation("Kandy");
        land.setTotalValue(BigDecimal.valueOf(600000.00));
        land.setMinimumInvestment(BigDecimal.valueOf(25000.00));
        land.setProgressPercentage(75);
        land.setIsActive(true);
        land.setCreatedAt(LocalDateTime.of(2026, 1, 10, 8, 0));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AC-1 + AC-2 + AC-3 + AC-4 — Happy path: approved milestones returned
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * AC-1: project detail fields are present in the response.
     * AC-2: only APPROVED milestones are fetched and returned.
     * AC-3: each milestone DTO has progressPercentage, notes, date, approvalStatus.
     * AC-4: milestones are ordered latest-first (repository guarantees DESC order).
     */
    @Test
    void getApprovedMilestones_shouldReturnProjectDetailWithApprovedMilestonesLatestFirst() {
        // Arrange
        Long investorId = investor.getUserId();
        Long landId = land.getLandId();

        Milestone newerMilestone = buildMilestone(1L, 75, "Irrigation installed",
                LocalDate.of(2026, 3, 15), MilestoneStatus.APPROVED);
        Milestone olderMilestone = buildMilestone(2L, 50, "Land cleared",
                LocalDate.of(2026, 2, 10), MilestoneStatus.APPROVED);

        Investment investment = buildInvestment(501L, BigDecimal.valueOf(150000.00));

        given(landRepository.findById(landId)).willReturn(Optional.of(land));
        given(milestoneRepository.existsByInvestorAndLand(investorId, landId)).willReturn(true);
        given(investmentRepository.findAllByUserIdWithLand(investorId))
                .willReturn(List.of(investment));
        given(milestoneRepository.findByLandIdAndStatus(landId, MilestoneStatus.APPROVED))
                .willReturn(List.of(newerMilestone, olderMilestone)); // repository returns DESC

        // Act
        ProjectMilestoneResponseDto result = milestoneService.getApprovedMilestones(investorId, landId);

        // Assert — AC-1: project detail
        assertEquals(landId, result.landId());
        assertEquals("Pepper Estate Phase 1", result.projectName());
        assertEquals("Kandy", result.location());
        assertEquals(BigDecimal.valueOf(600000.00), result.totalValue());
        assertEquals(BigDecimal.valueOf(150000.00), result.amountInvested());

        // AC-4: overallProgress present for visual progress bar
        assertEquals(75, result.overallProgress());

        // AC-2: two APPROVED milestones
        List<MilestoneDto> milestones = result.approvedMilestones();
        assertEquals(2, milestones.size());

        // AC-4: latest milestone is first
        assertEquals(1L, milestones.get(0).milestoneId());
        assertEquals(LocalDate.of(2026, 3, 15), milestones.get(0).date());

        // AC-3: all required milestone fields present
        assertEquals(75, milestones.get(0).progressPercentage());
        assertEquals("Irrigation installed", milestones.get(0).notes());
        assertEquals("APPROVED", milestones.get(0).approvalStatus());

        assertEquals(2L, milestones.get(1).milestoneId());
        assertEquals(50, milestones.get(1).progressPercentage());
        assertEquals("Land cleared", milestones.get(1).notes());
        assertEquals(LocalDate.of(2026, 2, 10), milestones.get(1).date());
        assertEquals("APPROVED", milestones.get(1).approvalStatus());
    }

    /**
     * AC-3: When a milestone has no notes (null), the DTO still maps correctly
     *       and no NullPointerException is thrown.
     */
    @Test
    void getApprovedMilestones_shouldHandleMilestoneWithNullNotesGracefully() {
        // Arrange
        Long investorId = investor.getUserId();
        Long landId = land.getLandId();

        Milestone milestoneWithNoNotes = buildMilestone(3L, 25, null,
                LocalDate.of(2026, 1, 20), MilestoneStatus.APPROVED);

        given(landRepository.findById(landId)).willReturn(Optional.of(land));
        given(milestoneRepository.existsByInvestorAndLand(investorId, landId)).willReturn(true);
        given(investmentRepository.findAllByUserIdWithLand(investorId))
                .willReturn(List.of(buildInvestment(502L, BigDecimal.valueOf(50000.00))));
        given(milestoneRepository.findByLandIdAndStatus(landId, MilestoneStatus.APPROVED))
                .willReturn(List.of(milestoneWithNoNotes));

        // Act
        ProjectMilestoneResponseDto result = milestoneService.getApprovedMilestones(investorId, landId);

        // Assert
        assertEquals(1, result.approvedMilestones().size());
        assertNull(result.approvedMilestones().get(0).notes());
        assertEquals("APPROVED", result.approvedMilestones().get(0).approvalStatus());
    }

    /**
     * AC-4: When no APPROVED milestones exist yet, the response still includes the
     *       project header and an empty list (so the UI can show an empty timeline).
     */
    @Test
    void getApprovedMilestones_shouldReturnEmptyMilestoneListWhenNoneApprovedYet() {
        // Arrange
        Long investorId = investor.getUserId();
        Long landId = land.getLandId();

        given(landRepository.findById(landId)).willReturn(Optional.of(land));
        given(milestoneRepository.existsByInvestorAndLand(investorId, landId)).willReturn(true);
        given(investmentRepository.findAllByUserIdWithLand(investorId))
                .willReturn(List.of(buildInvestment(503L, BigDecimal.valueOf(100000.00))));
        given(milestoneRepository.findByLandIdAndStatus(landId, MilestoneStatus.APPROVED))
                .willReturn(List.of());

        // Act
        ProjectMilestoneResponseDto result = milestoneService.getApprovedMilestones(investorId, landId);

        // Assert
        assertNotNull(result);
        assertEquals(landId, result.landId());
        assertTrue(result.approvedMilestones().isEmpty());
    }

    /**
     * AC-3 + AC-4: amountInvested is the sum of all investments by this investor
     *              in the given land (multiple investments are added together).
     */
    @Test
    void getApprovedMilestones_shouldSumMultipleInvestmentsForAmountInvested() {
        // Arrange
        Long investorId = investor.getUserId();
        Long landId = land.getLandId();

        Investment first  = buildInvestment(504L, BigDecimal.valueOf(80000.00));
        Investment second = buildInvestment(505L, BigDecimal.valueOf(70000.00));

        given(landRepository.findById(landId)).willReturn(Optional.of(land));
        given(milestoneRepository.existsByInvestorAndLand(investorId, landId)).willReturn(true);
        given(investmentRepository.findAllByUserIdWithLand(investorId))
                .willReturn(List.of(first, second));
        given(milestoneRepository.findByLandIdAndStatus(landId, MilestoneStatus.APPROVED))
                .willReturn(List.of());

        // Act
        ProjectMilestoneResponseDto result = milestoneService.getApprovedMilestones(investorId, landId);

        // Assert
        assertEquals(BigDecimal.valueOf(150000.00), result.amountInvested());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AC-5 — PENDING and REJECTED milestones must NOT appear
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * AC-5: The repository query is called with MilestoneStatus.APPROVED only.
     *       This verifies that no PENDING or REJECTED status is ever passed.
     */
    @Test
    void getApprovedMilestones_shouldQueryRepositoryWithApprovedStatusOnly() {
        // Arrange
        Long investorId = investor.getUserId();
        Long landId = land.getLandId();

        given(landRepository.findById(landId)).willReturn(Optional.of(land));
        given(milestoneRepository.existsByInvestorAndLand(investorId, landId)).willReturn(true);
        given(investmentRepository.findAllByUserIdWithLand(investorId))
                .willReturn(List.of(buildInvestment(506L, BigDecimal.valueOf(25000.00))));
        given(milestoneRepository.findByLandIdAndStatus(landId, MilestoneStatus.APPROVED))
                .willReturn(List.of());

        // Act
        milestoneService.getApprovedMilestones(investorId, landId);

        // Assert: APPROVED was used — PENDING and REJECTED were never queried
        verify(milestoneRepository).findByLandIdAndStatus(landId, MilestoneStatus.APPROVED);
        verify(milestoneRepository, never()).findByLandIdAndStatus(landId, MilestoneStatus.PENDING);
        verify(milestoneRepository, never()).findByLandIdAndStatus(landId, MilestoneStatus.REJECTED);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Error paths
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * AC-1: If the project (land) does not exist, a ResourceNotFoundException is thrown.
     */
    @Test
    void getApprovedMilestones_shouldThrowResourceNotFoundExceptionWhenProjectDoesNotExist() {
        // Arrange
        Long investorId = investor.getUserId();
        Long nonExistentLandId = 9999L;

        given(landRepository.findById(nonExistentLandId)).willReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> milestoneService.getApprovedMilestones(investorId, nonExistentLandId));

        assertEquals("Project not found with id: " + nonExistentLandId, exception.getMessage());

        // Service must not proceed to milestone lookup if project is missing
        verify(milestoneRepository, never())
                .findByLandIdAndStatus(nonExistentLandId, MilestoneStatus.APPROVED);
    }

    /**
     * AC-2 / AC-5: An investor who has NOT funded this project receives 403 FORBIDDEN
     *              via ResponseStatusException.
     */
    @Test
    void getApprovedMilestones_shouldThrow403WhenInvestorHasNotFundedProject() {
        // Arrange
        Long investorId = investor.getUserId();
        Long landId = land.getLandId();

        given(landRepository.findById(landId)).willReturn(Optional.of(land));
        given(milestoneRepository.existsByInvestorAndLand(investorId, landId)).willReturn(false);

        // Act & Assert
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> milestoneService.getApprovedMilestones(investorId, landId));

        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertTrue(exception.getReason().contains("You have not invested in this project"));

        // Milestone query must not be reached
        verify(milestoneRepository, never())
                .findByLandIdAndStatus(landId, MilestoneStatus.APPROVED);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper builders
    // ─────────────────────────────────────────────────────────────────────────

    private Milestone buildMilestone(Long id, int progress, String notes,
                                     LocalDate date, MilestoneStatus status) {
        Milestone m = new Milestone();
        m.setMilestoneId(id);
        m.setLand(land);
        m.setProgressPercentage(progress);
        m.setNotes(notes);
        m.setMilestoneDate(date);
        m.setStatus(status);
        return m;
    }

    private Investment buildInvestment(Long id, BigDecimal amount) {
        Investment investment = new Investment();
        investment.setInvestmentId(id);
        investment.setAmountInvested(amount);
        investment.setInvestor(investor);
        investment.setLand(land);
        investment.setStatus(Investment.InvestmentStatus.ACTIVE);
        investment.setInvestmentDate(LocalDateTime.of(2026, 1, 15, 9, 0));
        return investment;
    }
}