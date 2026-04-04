package CHC.Team.Ceylon.Harvest.Capital;

import CHC.Team.Ceylon.Harvest.Capital.config.WebConfig;
import CHC.Team.Ceylon.Harvest.Capital.controller.MilestoneEvidenceController;
import CHC.Team.Ceylon.Harvest.Capital.dto.EvidenceFileResponse;
import CHC.Team.Ceylon.Harvest.Capital.dto.MilestoneDetailResponse;
import CHC.Team.Ceylon.Harvest.Capital.enums.MilestoneStatus;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.security.RoleInterceptor;
import CHC.Team.Ceylon.Harvest.Capital.service.MilestoneService;
import CHC.Team.Ceylon.Harvest.Capital.service.SupabaseStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// AC-7: only FARMER role is permitted to upload milestone evidence
// AC-7: requests without a token are rejected with 401
// AC-7: requests with an AUDITOR or INVESTOR token are rejected with 403
@WebMvcTest(MilestoneEvidenceController.class)
@Import({WebConfig.class, RoleInterceptor.class})
class MilestoneEvidenceAccessControlWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MilestoneService milestoneService;

    @MockitoBean
    private SupabaseStorageService supabaseStorageService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JwtUtil jwtUtil;

    // ── AC-7: FARMER is allowed ───────────────────────────────────────────────

    @Test
    void evidenceUpload_shouldAllowFarmerRole() throws Exception {
        stubValidTokenWithRole("FARMER");

        // Stub service layer so the controller's Map.of() never receives null values
        String storedUrl = "https://supabase.test/storage/v1/object/public/evidence/milestones/uuid1.jpg";
        given(supabaseStorageService.upload(any())).willReturn(storedUrl);
        given(milestoneService.attachEvidenceFiles(any(), any(), any()))
                .willReturn(buildMilestoneDetail(
                        List.of(new EvidenceFileResponse("photo.jpg", storedUrl))));

        MockMultipartFile file = new MockMultipartFile(
                "files", "photo.jpg", "image/jpeg", "bytes".getBytes());

        mockMvc.perform(multipart("/api/farmer/milestones/101/evidence")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer good-token"))
                .andExpect(status().isOk());
    }

    // ── AC-7: AUDITOR is rejected ─────────────────────────────────────────────

    @Test
    void evidenceUpload_shouldRejectAuditorRoleWith403() throws Exception {
        stubValidTokenWithRole("AUDITOR");

        MockMultipartFile file = new MockMultipartFile(
                "files", "photo.jpg", "image/jpeg", "bytes".getBytes());

        mockMvc.perform(multipart("/api/farmer/milestones/101/evidence")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer good-token"))
                .andExpect(status().isForbidden());
    }

    // ── AC-7: INVESTOR is rejected ────────────────────────────────────────────

    @Test
    void evidenceUpload_shouldRejectInvestorRoleWith403() throws Exception {
        stubValidTokenWithRole("INVESTOR");

        MockMultipartFile file = new MockMultipartFile(
                "files", "photo.jpg", "image/jpeg", "bytes".getBytes());

        mockMvc.perform(multipart("/api/farmer/milestones/101/evidence")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer good-token"))
                .andExpect(status().isForbidden());
    }

    // ── AC-7: ADMIN is rejected ───────────────────────────────────────────────

    @Test
    void evidenceUpload_shouldRejectAdminRoleWith403() throws Exception {
        stubValidTokenWithRole("ADMIN");

        MockMultipartFile file = new MockMultipartFile(
                "files", "photo.jpg", "image/jpeg", "bytes".getBytes());

        mockMvc.perform(multipart("/api/farmer/milestones/101/evidence")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer good-token"))
                .andExpect(status().isForbidden());
    }

    // ── AC-7: missing token is rejected ──────────────────────────────────────

    @Test
    void evidenceUpload_shouldRejectMissingTokenWith401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "photo.jpg", "image/jpeg", "bytes".getBytes());

        mockMvc.perform(multipart("/api/farmer/milestones/101/evidence")
                        .file(file))
                .andExpect(status().isUnauthorized());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void stubValidTokenWithRole(String role) {
        given(jwtUtil.validateToken(anyString())).willReturn(true);
        given(jwtUtil.extractRole(anyString())).willReturn(role);
        given(jwtUtil.extractUserId(anyString())).willReturn("10");
    }

    private MilestoneDetailResponse buildMilestoneDetail(List<EvidenceFileResponse> evidenceFiles) {
        return new MilestoneDetailResponse(
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
                evidenceFiles
        );
    }
}