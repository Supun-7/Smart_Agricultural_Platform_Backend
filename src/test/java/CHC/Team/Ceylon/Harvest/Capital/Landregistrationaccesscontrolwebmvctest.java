package CHC.Team.Ceylon.Harvest.Capital;

import CHC.Team.Ceylon.Harvest.Capital.config.WebConfig;
import CHC.Team.Ceylon.Harvest.Capital.controller.FarmerController;
import CHC.Team.Ceylon.Harvest.Capital.repository.FarmerApplicationRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.security.RoleInterceptor;
import CHC.Team.Ceylon.Harvest.Capital.service.FarmerDashboardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Access-control (WebMvc) tests for CHC-275: Register Land & Display as Investment Listing.
 *
 * AC-1: Land registration endpoints are accessible from the farmer dashboard
 *       (verified: FARMER role is passed through by the RoleInterceptor without 401/403).
 * AC-6: Only the logged-in farmer can create and manage their land listings.
 *       — Non-farmer roles (INVESTOR, ADMIN, AUDITOR) → 403 Forbidden.
 *       — Missing Authorization header → 401 Unauthorized.
 */
@WebMvcTest(FarmerController.class)
@Import({WebConfig.class, RoleInterceptor.class})
class LandRegistrationAccessControlWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private FarmerApplicationRepository farmerApplicationRepository;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private FarmerDashboardService farmerDashboardService;

    // ── Helper ────────────────────────────────────────────────────────────────

    private void stubValidTokenWithRole(String role) {
        given(jwtUtil.validateToken(anyString())).willReturn(true);
        given(jwtUtil.extractRole(anyString())).willReturn(role);
        given(jwtUtil.extractUserId(anyString())).willReturn("10");
    }

    /** Minimal valid JSON body satisfying all @NotBlank / @NotNull / @DecimalMin constraints. */
    private static final String VALID_LAND_JSON = """
            {
              "projectName": "Green Valley Tea Plot",
              "location": "Kandy",
              "sizeAcres": 5.50,
              "cropType": "Tea",
              "description": "Fertile tea cultivation land in the central highlands with established bushes.",
              "imageUrls": "https://example.com/land1.jpg",
              "totalValue": 500000.00,
              "minimumInvestment": 10000.00
            }
            """;

    // ── POST /api/farmer/lands ─────────────────────────────────────────────

    /**
     * AC-1 / AC-6: A request with a FARMER role token reaches the land creation
     * endpoint — the RoleInterceptor must not block it with 401 or 403.
     */
    @Test
    void createLandListing_withFarmerRole_shouldBeAllowed() throws Exception {
        stubValidTokenWithRole("FARMER");

        mockMvc.perform(post("/api/farmer/lands")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer good-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_LAND_JSON))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status != 401 && status != 403
                            : "FARMER should reach the land endpoint, but got HTTP " + status;
                });
    }

    /**
     * AC-6: INVESTOR, ADMIN, and AUDITOR roles must be rejected with 403
     * when attempting to create a land listing.
     */
    @ParameterizedTest
    @ValueSource(strings = {"INVESTOR", "ADMIN", "AUDITOR"})
    void createLandListing_withNonFarmerRole_shouldReturn403(String role) throws Exception {
        stubValidTokenWithRole(role);

        mockMvc.perform(post("/api/farmer/lands")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer good-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_LAND_JSON))
                .andExpect(status().isForbidden());
    }

    /**
     * AC-6: POST /api/farmer/lands with no Authorization header must return 401.
     */
    @Test
    void createLandListing_withMissingAuthorizationHeader_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/farmer/lands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_LAND_JSON))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/farmer/lands ──────────────────────────────────────────────

    /**
     * AC-1 / AC-6: A FARMER token is accepted on GET /api/farmer/lands.
     */
    @Test
    void getMyLandListings_withFarmerRole_shouldBeAllowed() throws Exception {
        stubValidTokenWithRole("FARMER");

        mockMvc.perform(get("/api/farmer/lands")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer good-token"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status != 401 && status != 403
                            : "FARMER should reach the land listings endpoint, but got HTTP " + status;
                });
    }

    /**
     * AC-6: Non-farmer roles must not retrieve the farmer's land listings → 403.
     */
    @ParameterizedTest
    @ValueSource(strings = {"INVESTOR", "ADMIN", "AUDITOR"})
    void getMyLandListings_withNonFarmerRole_shouldReturn403(String role) throws Exception {
        stubValidTokenWithRole(role);

        mockMvc.perform(get("/api/farmer/lands")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer good-token"))
                .andExpect(status().isForbidden());
    }

    /**
     * AC-6: GET /api/farmer/lands with no Authorization header must return 401.
     */
    @Test
    void getMyLandListings_withMissingAuthorizationHeader_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/farmer/lands"))
                .andExpect(status().isUnauthorized());
    }

    // ── PATCH /api/farmer/lands/{landId}/active ────────────────────────────

    /**
     * AC-6: A FARMER token is accepted on PATCH /api/farmer/lands/{landId}/active.
     */
    @Test
    void updateLandStatus_withFarmerRole_shouldBeAllowed() throws Exception {
        stubValidTokenWithRole("FARMER");

        mockMvc.perform(patch("/api/farmer/lands/1/active")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer good-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status != 401 && status != 403
                            : "FARMER should be able to update land status, but got HTTP " + status;
                });
    }

    /**
     * AC-6: Non-farmer roles must not manage land listing status → 403.
     */
    @ParameterizedTest
    @ValueSource(strings = {"INVESTOR", "ADMIN", "AUDITOR"})
    void updateLandStatus_withNonFarmerRole_shouldReturn403(String role) throws Exception {
        stubValidTokenWithRole(role);

        mockMvc.perform(patch("/api/farmer/lands/1/active")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer good-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isForbidden());
    }

    /**
     * AC-6: PATCH /api/farmer/lands/{landId}/active with no Authorization header must return 401.
     */
    @Test
    void updateLandStatus_withMissingAuthorizationHeader_shouldReturn401() throws Exception {
        mockMvc.perform(patch("/api/farmer/lands/1/active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\": false}"))
                .andExpect(status().isUnauthorized());
    }
}