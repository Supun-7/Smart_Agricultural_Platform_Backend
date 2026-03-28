package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.dto.MilestoneDto;
import CHC.Team.Ceylon.Harvest.Capital.dto.ProjectMilestoneResponseDto;
import CHC.Team.Ceylon.Harvest.Capital.exception.GlobalExceptionHandler;
import CHC.Team.Ceylon.Harvest.Capital.repository.KycSubmissionRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.service.InvestorDashboardService;
import CHC.Team.Ceylon.Harvest.Capital.service.InvestorMilestoneService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WebMvcTest for the GET /api/investor/projects/{landId}/milestones endpoint.
 *
 * Story: CHC-112 — Investor View Project Progress and Milestone Status
 *
 * AC-1: An investor can navigate to a project detail page from their dashboard.
 * AC-2: Only APPROVED milestones are visible to the investor.
 * AC-3: Each milestone displays progressPercentage, notes, date, and approvalStatus.
 * AC-4: A visual milestone timeline / progress bar is shown per project.
 * AC-5: PENDING and REJECTED milestones are not visible to the investor.
 */
@ExtendWith(MockitoExtension.class)
class InvestorMilestoneControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private KycSubmissionRepository kycSubmissionRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private InvestorDashboardService investorDashboardService;

    @Mock
    private InvestorMilestoneService milestoneService;

    private MockMvc mockMvc;

    @org.springframework.web.bind.annotation.ControllerAdvice
    static class TestExceptionHandler {
        @org.springframework.web.bind.annotation.ExceptionHandler(CHC.Team.Ceylon.Harvest.Capital.exception.ResourceNotFoundException.class)
        public org.springframework.http.ResponseEntity<String> handleResourceNotFoundException(CHC.Team.Ceylon.Harvest.Capital.exception.ResourceNotFoundException ex) {
            return new org.springframework.http.ResponseEntity<>(ex.getMessage(), org.springframework.http.HttpStatus.NOT_FOUND);
        }
    }

    @BeforeEach
    void setUp() {
        InvestorController investorController = new InvestorController(
                userRepository,
                kycSubmissionRepository,
                jwtUtil,
                investorDashboardService,
                milestoneService);

        mockMvc = MockMvcBuilders.standaloneSetup(investorController)
                .setControllerAdvice(new GlobalExceptionHandler(), new TestExceptionHandler())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AC-1 + AC-2 + AC-3 + AC-4 — Happy path
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * AC-1: Investor navigates to project detail page.
     * AC-2: Only APPROVED milestones are returned.
     * AC-3: Each milestone contains progressPercentage, notes, date, approvalStatus.
     * AC-4: Response includes overallProgress for the visual progress bar.
     */
    @Test
    void getProjectMilestones_shouldReturnApprovedMilestonesWithAllRequiredFields() throws Exception {
        // Arrange
        Long investorId = 10L;
        Long landId = 71L;

        MilestoneDto milestone1 = new MilestoneDto(
                1L,
                75,
                "Irrigation system installed and soil tested",
                LocalDate.of(2026, 3, 15),
                "APPROVED");

        MilestoneDto milestone2 = new MilestoneDto(
                2L,
                50,
                "Seedling beds prepared, 50% land cleared",
                LocalDate.of(2026, 2, 10),
                "APPROVED");

        ProjectMilestoneResponseDto response = new ProjectMilestoneResponseDto(
                landId,
                "Pepper Estate Phase 1",
                "Kandy",
                BigDecimal.valueOf(600000.00),
                BigDecimal.valueOf(150000.00),
                75,
                List.of(milestone1, milestone2));

        when(jwtUtil.extractUserId("qa-token")).thenReturn(String.valueOf(investorId));
        when(milestoneService.getApprovedMilestones(investorId, landId)).thenReturn(response);

        // Act & Assert
        mockMvc.perform(get("/api/investor/projects/{landId}/milestones", landId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer qa-token"))
                .andExpect(status().isOk())
                // AC-1: project detail fields present
                .andExpect(jsonPath("$.landId").value(71))
                .andExpect(jsonPath("$.projectName").value("Pepper Estate Phase 1"))
                .andExpect(jsonPath("$.location").value("Kandy"))
                .andExpect(jsonPath("$.totalValue").value(600000.00))
                .andExpect(jsonPath("$.amountInvested").value(150000.00))
                // AC-4: overall progress for visual timeline/progress bar
                .andExpect(jsonPath("$.overallProgress").value(75))
                // AC-2: two APPROVED milestones returned
                .andExpect(jsonPath("$.approvedMilestones.length()").value(2))
                // AC-3: first milestone fields
                .andExpect(jsonPath("$.approvedMilestones[0].milestoneId").value(1))
                .andExpect(jsonPath("$.approvedMilestones[0].progressPercentage").value(75))
                .andExpect(jsonPath("$.approvedMilestones[0].notes").value("Irrigation system installed and soil tested"))
                .andExpect(jsonPath("$.approvedMilestones[0].date").value("2026-03-15"))
                .andExpect(jsonPath("$.approvedMilestones[0].approvalStatus").value("APPROVED"))
                // AC-3: second milestone fields
                .andExpect(jsonPath("$.approvedMilestones[1].milestoneId").value(2))
                .andExpect(jsonPath("$.approvedMilestones[1].progressPercentage").value(50))
                .andExpect(jsonPath("$.approvedMilestones[1].notes").value("Seedling beds prepared, 50% land cleared"))
                .andExpect(jsonPath("$.approvedMilestones[1].date").value("2026-02-10"))
                .andExpect(jsonPath("$.approvedMilestones[1].approvalStatus").value("APPROVED"));

        verify(milestoneService).getApprovedMilestones(investorId, landId);
    }

    /**
     * AC-4: When a project has no milestones yet, an empty list is returned and
     *       the project detail fields (including overallProgress) are still present.
     */
    @Test
    void getProjectMilestones_shouldReturnEmptyListWhenNoApprovedMilestonesExist() throws Exception {
        // Arrange
        Long investorId = 10L;
        Long landId = 71L;

        ProjectMilestoneResponseDto response = new ProjectMilestoneResponseDto(
                landId,
                "Pepper Estate Phase 1",
                "Kandy",
                BigDecimal.valueOf(600000.00),
                BigDecimal.valueOf(150000.00),
                0,
                List.of());

        when(jwtUtil.extractUserId("qa-token")).thenReturn(String.valueOf(investorId));
        when(milestoneService.getApprovedMilestones(investorId, landId)).thenReturn(response);

        // Act & Assert
        mockMvc.perform(get("/api/investor/projects/{landId}/milestones", landId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer qa-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.landId").value(71))
                .andExpect(jsonPath("$.overallProgress").value(0))
                .andExpect(jsonPath("$.approvedMilestones.length()").value(0));

        verify(milestoneService).getApprovedMilestones(investorId, landId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AC-2 / AC-5 — Authorisation: investor must have funded the project
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * AC-2 / AC-5: An investor who has NOT funded this project receives 403 FORBIDDEN.
     * The service layer enforces this; the controller must propagate the status.
     */
    @Test
    void getProjectMilestones_shouldReturn403WhenInvestorHasNotFundedProject() throws Exception {
        // Arrange
        Long investorId = 10L;
        Long landId = 99L;

        when(jwtUtil.extractUserId("qa-token")).thenReturn(String.valueOf(investorId));
        when(milestoneService.getApprovedMilestones(investorId, landId))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "You have not invested in this project"));

        // Act & Assert
        mockMvc.perform(get("/api/investor/projects/{landId}/milestones", landId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer qa-token"))
                .andExpect(status().isForbidden());

        verify(milestoneService).getApprovedMilestones(investorId, landId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AC-1 — Project not found
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * AC-1: Navigating to a project detail page for a non-existent project
     *       returns 404 NOT FOUND.
     */
    @Test
    void getProjectMilestones_shouldReturn404WhenProjectDoesNotExist() throws Exception {
        // Arrange
        Long investorId = 10L;
        Long landId = 9999L;

        when(jwtUtil.extractUserId("qa-token")).thenReturn(String.valueOf(investorId));
        when(milestoneService.getApprovedMilestones(investorId, landId))
                .thenThrow(new CHC.Team.Ceylon.Harvest.Capital.exception.ResourceNotFoundException(
                        "Project not found with id: " + landId));

        // Act & Assert
        mockMvc.perform(get("/api/investor/projects/{landId}/milestones", landId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer qa-token"))
                .andExpect(status().isNotFound());

        verify(milestoneService).getApprovedMilestones(investorId, landId);
    }
}