package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.dto.EvidenceFileResponse;
import CHC.Team.Ceylon.Harvest.Capital.dto.MilestoneDetailResponse;
import CHC.Team.Ceylon.Harvest.Capital.dto.MilestoneSummaryResponse;
import CHC.Team.Ceylon.Harvest.Capital.enums.MilestoneStatus;
import CHC.Team.Ceylon.Harvest.Capital.exception.ConflictException;
import CHC.Team.Ceylon.Harvest.Capital.exception.GlobalExceptionHandler;
import CHC.Team.Ceylon.Harvest.Capital.service.MilestoneService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// AC-1: pending milestones endpoint returns all pending milestones for the auditor dashboard
// AC-2: list entries expose farmer name, project name, progress %, notes, and milestone date
// AC-3: milestone detail view returns uploaded evidence files
// AC-4: auditor can approve a pending milestone
// AC-5: auditor can reject a pending milestone only with a mandatory reason
// AC-8: already actioned milestones cannot be approved or rejected again
@ExtendWith(MockitoExtension.class)
class MilestoneAuditorControllerTest {

    @Mock
    private MilestoneService milestoneService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MilestoneAuditorController controller = new MilestoneAuditorController(milestoneService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getPendingMilestones_shouldReturnDashboardList() throws Exception {
        MilestoneSummaryResponse item1 = new MilestoneSummaryResponse(
                101L,
                "Nuwan Perera",
                "Kandy Tea Estate",
                45,
                "Irrigation completed",
                LocalDate.of(2026, 3, 18),
                MilestoneStatus.PENDING);

        MilestoneSummaryResponse item2 = new MilestoneSummaryResponse(
                102L,
                "Amali Silva",
                "Matale Spice Farm",
                60,
                "Seedlings verified",
                LocalDate.of(2026, 3, 20),
                MilestoneStatus.PENDING);

        when(milestoneService.getPendingMilestones()).thenReturn(List.of(item1, item2));

        mockMvc.perform(get("/api/auditor/milestones/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].farmerName").value("Nuwan Perera"))
                .andExpect(jsonPath("$.items[0].projectName").value("Kandy Tea Estate"))
                .andExpect(jsonPath("$.items[0].progressPercentage").value(45))
                .andExpect(jsonPath("$.items[0].notes").value("Irrigation completed"))
                .andExpect(jsonPath("$.items[0].milestoneDate").value("2026-03-18"))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"));
    }

    @Test
    void getMilestoneDetail_shouldReturnEvidenceFiles() throws Exception {
        MilestoneDetailResponse detail = new MilestoneDetailResponse(
                101L,
                "Nuwan Perera",
                "nuwan@farm.lk",
                "Kandy Tea Estate",
                45,
                "Irrigation completed",
                LocalDate.of(2026, 3, 18),
                MilestoneStatus.PENDING,
                null,
                null,
                null,
                LocalDateTime.of(2026, 3, 18, 9, 30),
                List.of(
                        new EvidenceFileResponse("photo-1.jpg", "https://cdn.test/photo-1.jpg"),
                        new EvidenceFileResponse("report.pdf", "https://cdn.test/report.pdf")
                )
        );

        when(milestoneService.getMilestoneDetail(101L)).thenReturn(detail);

        mockMvc.perform(get("/api/auditor/milestones/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.farmerName").value("Nuwan Perera"))
                .andExpect(jsonPath("$.projectName").value("Kandy Tea Estate"))
                .andExpect(jsonPath("$.evidenceFiles.length()").value(2))
                .andExpect(jsonPath("$.evidenceFiles[0].name").value("photo-1.jpg"))
                .andExpect(jsonPath("$.evidenceFiles[1].url").value("https://cdn.test/report.pdf"));
    }

    @Test
    void approveMilestone_shouldReturnApprovedMilestone() throws Exception {
        MilestoneDetailResponse approved = new MilestoneDetailResponse(
                101L,
                "Nuwan Perera",
                "nuwan@farm.lk",
                "Kandy Tea Estate",
                45,
                "Irrigation completed",
                LocalDate.of(2026, 3, 18),
                MilestoneStatus.APPROVED,
                null,
                LocalDateTime.of(2026, 3, 25, 11, 15),
                "Ayesha Auditor",
                LocalDateTime.of(2026, 3, 18, 9, 30),
                List.of()
        );

        when(milestoneService.approveMilestone(101L, 88L)).thenReturn(approved);

        mockMvc.perform(put("/api/auditor/milestones/101/approve")
                        .requestAttr("userId", 88L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Milestone approved successfully"))
                .andExpect(jsonPath("$.milestone.status").value("APPROVED"))
                .andExpect(jsonPath("$.milestone.reviewedBy").value("Ayesha Auditor"));

        verify(milestoneService).approveMilestone(101L, 88L);
    }

    @Test
    void rejectMilestone_shouldRequireReasonAndReturnRejectedMilestone() throws Exception {
        MilestoneDetailResponse rejected = new MilestoneDetailResponse(
                101L,
                "Nuwan Perera",
                "nuwan@farm.lk",
                "Kandy Tea Estate",
                45,
                "Irrigation completed",
                LocalDate.of(2026, 3, 18),
                MilestoneStatus.REJECTED,
                "Evidence is blurry",
                LocalDateTime.of(2026, 3, 25, 12, 0),
                "Ayesha Auditor",
                LocalDateTime.of(2026, 3, 18, 9, 30),
                List.of()
        );

        when(milestoneService.rejectMilestone(101L, 88L, "Evidence is blurry")).thenReturn(rejected);

        mockMvc.perform(put("/api/auditor/milestones/101/reject")
                        .requestAttr("userId", 88L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Evidence is blurry\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Milestone rejected successfully"))
                .andExpect(jsonPath("$.milestone.status").value("REJECTED"))
                .andExpect(jsonPath("$.milestone.rejectionReason").value("Evidence is blurry"));

        verify(milestoneService).rejectMilestone(101L, 88L, "Evidence is blurry");
    }

    @Test
    void rejectMilestone_shouldFailValidationWhenReasonMissing() throws Exception {
        mockMvc.perform(put("/api/auditor/milestones/101/reject")
                        .requestAttr("userId", 88L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Rejection reason is required"));
    }

    @Test
    void approveMilestone_shouldReturnConflictWhenAlreadyActioned() throws Exception {
        when(milestoneService.approveMilestone(eq(101L), eq(88L)))
                .thenThrow(new ConflictException("Milestone has already been actioned"));

        mockMvc.perform(put("/api/auditor/milestones/101/approve")
                        .requestAttr("userId", 88L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Milestone has already been actioned"));
    }
}
