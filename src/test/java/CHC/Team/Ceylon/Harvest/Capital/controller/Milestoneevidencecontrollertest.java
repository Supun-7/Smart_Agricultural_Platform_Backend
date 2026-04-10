package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.dto.EvidenceFileResponse;
import CHC.Team.Ceylon.Harvest.Capital.dto.MilestoneDetailResponse;
import CHC.Team.Ceylon.Harvest.Capital.enums.MilestoneStatus;
import CHC.Team.Ceylon.Harvest.Capital.exception.BadRequestException;
import CHC.Team.Ceylon.Harvest.Capital.exception.ConflictException;
import CHC.Team.Ceylon.Harvest.Capital.exception.GlobalExceptionHandler;
import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.service.MilestoneService;
import CHC.Team.Ceylon.Harvest.Capital.service.SupabaseStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// AC-1: the endpoint accepts multipart/form-data with a "files" field
// AC-2: only JPG, PNG, and PDF file types are accepted; others are rejected
// AC-3: files exceeding 5 MB are rejected with an appropriate error
// AC-4: successfully uploaded files are linked to the milestone record and returned in the response
// AC-6: unsupported file type or oversized file returns a descriptive 400 error message
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MilestoneEvidenceControllerTest {

    @Mock
    private MilestoneService milestoneService;

    @Mock
    private SupabaseStorageService supabaseStorageService;

    @Mock
    private JwtUtil jwtUtil;

    private MockMvc mockMvc;

    private static final String VALID_TOKEN = "Bearer valid-farmer-token";
    private static final Long FARMER_USER_ID = 10L;
    private static final Long MILESTONE_ID = 101L;

    @BeforeEach
    void setUp() {
        MilestoneEvidenceController controller =
                new MilestoneEvidenceController(milestoneService, supabaseStorageService, jwtUtil);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        when(jwtUtil.extractUserId("valid-farmer-token")).thenReturn(String.valueOf(FARMER_USER_ID));
    }

    // ── AC-1 / AC-4: successful single JPG upload ─────────────────────────────

    @Test
    void uploadEvidence_shouldAcceptJpgAndReturnUpdatedMilestone() throws Exception {
        MockMultipartFile jpgFile = new MockMultipartFile(
                "files", "progress-photo.jpg", "image/jpeg", "fake-jpg-bytes".getBytes());

        String storedUrl = "https://supabase.test/storage/v1/object/public/evidence/milestones/uuid1.jpg";
        MilestoneDetailResponse updatedMilestone = buildMilestoneDetail(
                List.of(new EvidenceFileResponse("progress-photo.jpg", storedUrl)));

        when(supabaseStorageService.upload(any())).thenReturn(storedUrl);
        when(milestoneService.attachEvidenceFiles(eq(MILESTONE_ID), eq(FARMER_USER_ID), eq(List.of(storedUrl))))
                .thenReturn(updatedMilestone);

        mockMvc.perform(multipart("/api/farmer/milestones/{id}/evidence", MILESTONE_ID)
                        .file(jpgFile)
                        .header("Authorization", VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Evidence uploaded successfully."))
                .andExpect(jsonPath("$.uploadedCount").value(1))
                .andExpect(jsonPath("$.milestone.id").value(MILESTONE_ID))
                .andExpect(jsonPath("$.milestone.evidenceFiles.length()").value(1))
                .andExpect(jsonPath("$.milestone.evidenceFiles[0].name").value("progress-photo.jpg"))
                .andExpect(jsonPath("$.milestone.evidenceFiles[0].url").value(storedUrl));
    }

    // ── AC-1 / AC-4: successful PNG upload ────────────────────────────────────

    @Test
    void uploadEvidence_shouldAcceptPng() throws Exception {
        MockMultipartFile pngFile = new MockMultipartFile(
                "files", "field-snapshot.png", "image/png", "fake-png-bytes".getBytes());

        String storedUrl = "https://supabase.test/storage/v1/object/public/evidence/milestones/uuid2.png";
        MilestoneDetailResponse updatedMilestone = buildMilestoneDetail(
                List.of(new EvidenceFileResponse("field-snapshot.png", storedUrl)));

        when(supabaseStorageService.upload(any())).thenReturn(storedUrl);
        when(milestoneService.attachEvidenceFiles(eq(MILESTONE_ID), eq(FARMER_USER_ID), eq(List.of(storedUrl))))
                .thenReturn(updatedMilestone);

        mockMvc.perform(multipart("/api/farmer/milestones/{id}/evidence", MILESTONE_ID)
                        .file(pngFile)
                        .header("Authorization", VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadedCount").value(1))
                .andExpect(jsonPath("$.milestone.evidenceFiles[0].name").value("field-snapshot.png"));
    }

    // ── AC-1 / AC-4: successful PDF upload ────────────────────────────────────

    @Test
    void uploadEvidence_shouldAcceptPdf() throws Exception {
        MockMultipartFile pdfFile = new MockMultipartFile(
                "files", "audit-report.pdf", "application/pdf", "fake-pdf-bytes".getBytes());

        String storedUrl = "https://supabase.test/storage/v1/object/public/evidence/milestones/uuid3.pdf";
        MilestoneDetailResponse updatedMilestone = buildMilestoneDetail(
                List.of(new EvidenceFileResponse("audit-report.pdf", storedUrl)));

        when(supabaseStorageService.upload(any())).thenReturn(storedUrl);
        when(milestoneService.attachEvidenceFiles(eq(MILESTONE_ID), eq(FARMER_USER_ID), eq(List.of(storedUrl))))
                .thenReturn(updatedMilestone);

        mockMvc.perform(multipart("/api/farmer/milestones/{id}/evidence", MILESTONE_ID)
                        .file(pdfFile)
                        .header("Authorization", VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadedCount").value(1))
                .andExpect(jsonPath("$.milestone.evidenceFiles[0].name").value("audit-report.pdf"));
    }

    // ── AC-4: multiple files uploaded in one request ──────────────────────────

    @Test
    void uploadEvidence_shouldAcceptMultipleFilesAndReturnCorrectCount() throws Exception {
        MockMultipartFile jpg = new MockMultipartFile(
                "files", "photo.jpg", "image/jpeg", "jpg-bytes".getBytes());
        MockMultipartFile pdf = new MockMultipartFile(
                "files", "report.pdf", "application/pdf", "pdf-bytes".getBytes());

        String urlJpg = "https://supabase.test/storage/v1/object/public/evidence/milestones/uuid4.jpg";
        String urlPdf = "https://supabase.test/storage/v1/object/public/evidence/milestones/uuid5.pdf";

        MilestoneDetailResponse updatedMilestone = buildMilestoneDetail(List.of(
                new EvidenceFileResponse("photo.jpg", urlJpg),
                new EvidenceFileResponse("report.pdf", urlPdf)));

        when(supabaseStorageService.upload(any()))
                .thenReturn(urlJpg)
                .thenReturn(urlPdf);
        when(milestoneService.attachEvidenceFiles(eq(MILESTONE_ID), eq(FARMER_USER_ID), eq(List.of(urlJpg, urlPdf))))
                .thenReturn(updatedMilestone);

        mockMvc.perform(multipart("/api/farmer/milestones/{id}/evidence", MILESTONE_ID)
                        .file(jpg)
                        .file(pdf)
                        .header("Authorization", VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadedCount").value(2))
                .andExpect(jsonPath("$.milestone.evidenceFiles.length()").value(2));
    }

    // ── AC-2 / AC-6: unsupported file type rejected ───────────────────────────

    @Test
    void uploadEvidence_shouldRejectUnsupportedFileTypeWithDescriptiveMessage() throws Exception {
        MockMultipartFile gifFile = new MockMultipartFile(
                "files", "animation.gif", "image/gif", "fake-gif-bytes".getBytes());

        when(supabaseStorageService.upload(any()))
                .thenThrow(new BadRequestException("Unsupported file type. Only JPG, PNG, and PDF are accepted."));

        mockMvc.perform(multipart("/api/farmer/milestones/{id}/evidence", MILESTONE_ID)
                        .file(gifFile)
                        .header("Authorization", VALID_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Unsupported file type. Only JPG, PNG, and PDF are accepted."));
    }

    @Test
    void uploadEvidence_shouldRejectDocxFileType() throws Exception {
        MockMultipartFile docxFile = new MockMultipartFile(
                "files", "evidence.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "fake-docx-bytes".getBytes());

        when(supabaseStorageService.upload(any()))
                .thenThrow(new BadRequestException("Unsupported file type. Only JPG, PNG, and PDF are accepted."));

        mockMvc.perform(multipart("/api/farmer/milestones/{id}/evidence", MILESTONE_ID)
                        .file(docxFile)
                        .header("Authorization", VALID_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Unsupported file type. Only JPG, PNG, and PDF are accepted."));
    }

    // ── AC-3 / AC-6: oversized file rejected ─────────────────────────────────

    @Test
    void uploadEvidence_shouldRejectFilesExceeding5MbWithDescriptiveMessage() throws Exception {
        // Simulate a file that passes into the controller but the service validates size
        MockMultipartFile oversizedFile = new MockMultipartFile(
                "files", "large-photo.jpg", "image/jpeg", "a".repeat(100).getBytes());

        when(supabaseStorageService.upload(any()))
                .thenThrow(new BadRequestException("File size exceeds 5 MB limit. Please upload smaller file."));

        mockMvc.perform(multipart("/api/farmer/milestones/{id}/evidence", MILESTONE_ID)
                        .file(oversizedFile)
                        .header("Authorization", VALID_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("File size exceeds 5 MB limit. Please upload smaller file."));
    }

    // ── AC-1: missing files field returns 400 ────────────────────────────────

    @Test
    void uploadEvidence_shouldReturnBadRequestWhenNoFilesProvided() throws Exception {
        mockMvc.perform(multipart("/api/farmer/milestones/{id}/evidence", MILESTONE_ID)
                        .header("Authorization", VALID_TOKEN)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("At least one file must be provided."));
    }

    // ── Authorization: missing token returns 400 ─────────────────────────────

    @Test
    void uploadEvidence_shouldReturn400WhenAuthorizationHeaderIsMissing() throws Exception {
        MockMultipartFile jpg = new MockMultipartFile(
                "files", "photo.jpg", "image/jpeg", "bytes".getBytes());

        mockMvc.perform(multipart("/api/farmer/milestones/{id}/evidence", MILESTONE_ID)
                        .file(jpg))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing or invalid Authorization header."));
    }

    // ── AC-4: milestone not found / wrong farmer returns conflict ────────────

    @Test
    void uploadEvidence_shouldReturn409WhenMilestoneIsAlreadyActioned() throws Exception {
        MockMultipartFile jpg = new MockMultipartFile(
                "files", "photo.jpg", "image/jpeg", "bytes".getBytes());

        String storedUrl = "https://supabase.test/storage/v1/object/public/evidence/milestones/uuid6.jpg";
        when(supabaseStorageService.upload(any())).thenReturn(storedUrl);
        when(milestoneService.attachEvidenceFiles(any(), any(), any()))
                .thenThrow(new ConflictException("Evidence can only be uploaded to a PENDING milestone."));

        mockMvc.perform(multipart("/api/farmer/milestones/{id}/evidence", MILESTONE_ID)
                        .file(jpg)
                        .header("Authorization", VALID_TOKEN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Evidence can only be uploaded to a PENDING milestone."));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MilestoneDetailResponse buildMilestoneDetail(List<EvidenceFileResponse> evidenceFiles) {
        return new MilestoneDetailResponse(
                MILESTONE_ID,
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
                evidenceFiles
        );
    }
}