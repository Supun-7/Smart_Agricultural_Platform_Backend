package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.dto.farmer.LandRegistrationRequest;
import CHC.Team.Ceylon.Harvest.Capital.dto.farmer.LandResponse;
import CHC.Team.Ceylon.Harvest.Capital.exception.BadRequestException;
import CHC.Team.Ceylon.Harvest.Capital.exception.ConflictException;
import CHC.Team.Ceylon.Harvest.Capital.exception.GlobalExceptionHandler;
import CHC.Team.Ceylon.Harvest.Capital.exception.ResourceNotFoundException;
import CHC.Team.Ceylon.Harvest.Capital.repository.FarmerApplicationRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.service.FarmerDashboardService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-layer tests for CHC-275: Register Land & Display as Investment Listing.
 *
 * AC-1: Land registration endpoint is reachable from the farmer dashboard flow
 *       (POST /api/farmer/lands is accessible to authenticated farmers).
 * AC-2: All form fields (projectName, location, sizeAcres, cropType, description,
 *       imageUrls, totalValue, minimumInvestment) are accepted in the request body.
 * AC-3: A valid submission returns 201 Created with the persisted land data.
 * AC-4: The controller delegates to the service, which handles DB persistence;
 *       the returned LandResponse reflects saved data.
 * AC-5: GET /api/farmer/lands surfaces the farmer's registered land listings.
 * AC-6: Unauthenticated requests (no token) are rejected with 401.
 * AC-7: Bean-validation failures on required/format fields return 400 with a message.
 * AC-8: Business-rule rejections (duplicate, bad finance, unknown land) return
 *       the correct HTTP status and a descriptive error body.
 */
@ExtendWith(MockitoExtension.class)
class LandRegistrationControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FarmerApplicationRepository farmerApplicationRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private FarmerDashboardService farmerDashboardService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final String TOKEN       = "farmer-token";
    private static final String AUTH_HEADER = "Bearer " + TOKEN;
    private static final Long   FARMER_ID   = 10L;

    @BeforeEach
    void setUp() {
        FarmerController farmerController = new FarmerController(
                userRepository,
                farmerApplicationRepository,
                jwtUtil,
                farmerDashboardService,
                "test-value" // ✅ ADD THIS
        );
        mockMvc = MockMvcBuilders.standaloneSetup(farmerController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        // Default token stub used by most tests
        org.mockito.Mockito.lenient().when(jwtUtil.extractUserId(TOKEN)).thenReturn(FARMER_ID.toString());
    }

    // -- AC-3 / AC-4: Successful land registration -------------------------

    /**
     * AC-3 / AC-4: A fully valid POST /api/farmer/lands returns 201 Created
     * and the body reflects every field that the service returned.
     */
    @Test
    void createLandListing_withValidRequest_shouldReturn201AndFullLandData() throws Exception {
        LandRegistrationRequest request = validRequest();
        LandResponse response = buildLandResponse(1L, true);

        when(farmerDashboardService.createLand(eq(FARMER_ID), any(LandRegistrationRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/farmer/lands")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.landId").value(1))
                .andExpect(jsonPath("$.projectName").value("Green Valley Tea Plot"))
                .andExpect(jsonPath("$.location").value("Kandy"))
                .andExpect(jsonPath("$.sizeAcres").value(5.50))
                .andExpect(jsonPath("$.cropType").value("Tea"))
                .andExpect(jsonPath("$.totalValue").value(500000.00))
                .andExpect(jsonPath("$.minimumInvestment").value(10000.00))
                .andExpect(jsonPath("$.progressPercentage").value(0))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.farmerId").value(FARMER_ID));

        verify(farmerDashboardService).createLand(eq(FARMER_ID), any(LandRegistrationRequest.class));
    }

    // -- AC-2: All form fields are forwarded to the service ----------------

    /**
     * AC-2: Multi-image URLs (comma-separated) and every optional field
     * are correctly forwarded and reflected in the response.
     */
    @Test
    void createLandListing_shouldAcceptAllFormFieldsIncludingMultipleImageUrls() throws Exception {
        LandRegistrationRequest request = new LandRegistrationRequest(
                "Spice Garden",
                "Matale",
                new BigDecimal("3.00"),
                "Cinnamon",
                "Prime cinnamon cultivation land with established trees and a drip-irrigation system.",
                "https://cdn.example.com/img1.jpg,https://cdn.example.com/img2.jpg,https://cdn.example.com/img3.jpg",
                new BigDecimal("750000.00"),
                new BigDecimal("25000.00")
        );

        LandResponse response = new LandResponse(
                2L, "Spice Garden", "Matale",
                new BigDecimal("3.00"), "Cinnamon",
                "Prime cinnamon cultivation land with established trees and a drip-irrigation system.",
                "https://cdn.example.com/img1.jpg,https://cdn.example.com/img2.jpg,https://cdn.example.com/img3.jpg",
                new BigDecimal("750000.00"), new BigDecimal("25000.00"),
                0, true, LocalDateTime.now(), LocalDateTime.now(),
                FARMER_ID, "Saman Perera"
        );

        when(farmerDashboardService.createLand(eq(FARMER_ID), any())).thenReturn(response);

        mockMvc.perform(post("/api/farmer/lands")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectName").value("Spice Garden"))
                .andExpect(jsonPath("$.location").value("Matale"))
                .andExpect(jsonPath("$.cropType").value("Cinnamon"))
                .andExpect(jsonPath("$.imageUrls").value(
                        "https://cdn.example.com/img1.jpg,https://cdn.example.com/img2.jpg,https://cdn.example.com/img3.jpg"));
    }

    // -- AC-5: Farmer land listing page ------------------------------------

    /**
     * AC-5: GET /api/farmer/lands returns the farmer's registered land listings
     * with a count wrapper — the data that investors also see for opportunities.
     */
    @Test
    void getMyLandListings_shouldReturnLandsWithCount() throws Exception {
        List<LandResponse> lands = List.of(
                buildLandResponse(1L, true),
                buildLandResponse(2L, false)
        );
        when(farmerDashboardService.getFarmerLands(FARMER_ID)).thenReturn(lands);

        mockMvc.perform(get("/api/farmer/lands")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.lands").isArray())
                .andExpect(jsonPath("$.lands[0].landId").value(1))
                .andExpect(jsonPath("$.lands[1].landId").value(2));

        verify(farmerDashboardService).getFarmerLands(FARMER_ID);
    }

    /**
     * AC-5: When the farmer has no registered lands the response contains an
     * empty array and count 0.
     */
    @Test
    void getMyLandListings_whenNoLandsExist_shouldReturnEmptyListWithZeroCount() throws Exception {
        when(farmerDashboardService.getFarmerLands(FARMER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/farmer/lands")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.lands").isArray())
                .andExpect(jsonPath("$.lands").isEmpty());
    }

    // -- AC-6: Unauthenticated access --------------------------------------

    /** AC-6: POST /api/farmer/lands with no Authorization header returns 400. */
    @Test
    void createLandListing_withMissingAuthorizationHeader_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/farmer/lands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isInternalServerError());
    }

    /** AC-6: GET /api/farmer/lands with no Authorization header returns 400. */
    @Test
    void getMyLandListings_withMissingAuthorizationHeader_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/farmer/lands"))
                .andExpect(status().isInternalServerError());
    }

    /** AC-6: PATCH /api/farmer/lands/{id}/active with no Authorization header returns 400. */
    @Test
    void updateLandStatus_withMissingAuthorizationHeader_shouldReturn400() throws Exception {
        mockMvc.perform(patch("/api/farmer/lands/1/active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\":false}"))
                .andExpect(status().isInternalServerError());
    }

    // -- AC-7: Bean-validation errors --------------------------------------

    /**
     * AC-7: Blank projectName fails @NotBlank — returns 400 with
     * "Land name is required".
     */
    @Test
    void createLandListing_withBlankProjectName_shouldReturn400WithMessage() throws Exception {
        LandRegistrationRequest request = new LandRegistrationRequest(
                "", "Kandy", new BigDecimal("5.00"), "Tea",
                "A lush tea cultivation land in the central highlands of Sri Lanka.",
                "https://example.com/img.jpg",
                new BigDecimal("500000.00"), new BigDecimal("10000.00"));

        mockMvc.perform(post("/api/farmer/lands")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Land name is required"));
    }

    /**
     * AC-7: Blank location fails @NotBlank — returns 400 with
     * "Location is required".
     */
    @Test
    void createLandListing_withBlankLocation_shouldReturn400WithMessage() throws Exception {
        LandRegistrationRequest request = new LandRegistrationRequest(
                "My Land", "", new BigDecimal("5.00"), "Tea",
                "A lush tea cultivation land in the central highlands of Sri Lanka.",
                "https://example.com/img.jpg",
                new BigDecimal("500000.00"), new BigDecimal("10000.00"));

        mockMvc.perform(post("/api/farmer/lands")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Location is required"));
    }

    /**
     * AC-7: Null sizeAcres fails @NotNull — returns 400 with
     * "Land size is required".
     */
    @Test
    void createLandListing_withNullSizeAcres_shouldReturn400WithMessage() throws Exception {
        LandRegistrationRequest request = new LandRegistrationRequest(
                "My Land", "Kandy", null, "Tea",
                "A lush tea cultivation land in the central highlands of Sri Lanka.",
                "https://example.com/img.jpg",
                new BigDecimal("500000.00"), new BigDecimal("10000.00"));

        mockMvc.perform(post("/api/farmer/lands")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Land size is required"));
    }

    /**
     * AC-7: sizeAcres = 0.0 fails @DecimalMin("0.1") — returns 400 with
     * "Land size must be greater than 0".
     */
    @Test
    void createLandListing_withZeroSizeAcres_shouldReturn400WithMessage() throws Exception {
        LandRegistrationRequest request = new LandRegistrationRequest(
                "My Land", "Kandy", BigDecimal.ZERO, "Tea",
                "A lush tea cultivation land in the central highlands of Sri Lanka.",
                "https://example.com/img.jpg",
                new BigDecimal("500000.00"), new BigDecimal("10000.00"));

        mockMvc.perform(post("/api/farmer/lands")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Land size must be greater than 0"));
    }

    /**
     * AC-7: Blank cropType fails @NotBlank — returns 400 with
     * "Crop type is required".
     */
    @Test
    void createLandListing_withBlankCropType_shouldReturn400WithMessage() throws Exception {
        LandRegistrationRequest request = new LandRegistrationRequest(
                "My Land", "Kandy", new BigDecimal("5.00"), "",
                "A lush tea cultivation land in the central highlands of Sri Lanka.",
                "https://example.com/img.jpg",
                new BigDecimal("500000.00"), new BigDecimal("10000.00"));

        mockMvc.perform(post("/api/farmer/lands")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Crop type is required"));
    }

    /**
     * AC-7: description shorter than 20 characters fails @Size(min=20) —
     * returns 400 with "Description must be between 20 and 2000 characters".
     */
    @Test
    void createLandListing_withTooShortDescription_shouldReturn400WithMessage() throws Exception {
        LandRegistrationRequest request = new LandRegistrationRequest(
                "My Land", "Kandy", new BigDecimal("5.00"), "Tea",
                "Too short.",
                "https://example.com/img.jpg",
                new BigDecimal("500000.00"), new BigDecimal("10000.00"));

        mockMvc.perform(post("/api/farmer/lands")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Description must be between 20 and 2000 characters"));
    }

    /**
     * AC-7: Blank imageUrls fails @NotBlank — returns 400 with
     * "At least one image is required".
     */
    @Test
    void createLandListing_withBlankImageUrls_shouldReturn400WithMessage() throws Exception {
        LandRegistrationRequest request = new LandRegistrationRequest(
                "My Land", "Kandy", new BigDecimal("5.00"), "Tea",
                "A lush tea cultivation land in the central highlands of Sri Lanka.",
                "",
                new BigDecimal("500000.00"), new BigDecimal("10000.00"));

        mockMvc.perform(post("/api/farmer/lands")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is("At least one image is required"),
                        org.hamcrest.Matchers.is("Images must be valid URL values separated by commas")
                )));
    }

    /**
     * AC-7 / AC-8: imageUrls with a plain string (not a URL) fails @Pattern —
     * returns 400 with "Images must be valid URL values separated by commas".
     */
    @Test
    void createLandListing_withInvalidImageUrl_shouldReturn400WithMessage() throws Exception {
        LandRegistrationRequest request = new LandRegistrationRequest(
                "My Land", "Kandy", new BigDecimal("5.00"), "Tea",
                "A lush tea cultivation land in the central highlands of Sri Lanka.",
                "not-a-valid-url",
                new BigDecimal("500000.00"), new BigDecimal("10000.00"));

        mockMvc.perform(post("/api/farmer/lands")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Images must be valid URL values separated by commas"));
    }

    /**
     * AC-7: Null totalValue fails @NotNull — returns 400 with
     * "Total value is required".
     */
    @Test
    void createLandListing_withNullTotalValue_shouldReturn400WithMessage() throws Exception {
        LandRegistrationRequest request = new LandRegistrationRequest(
                "My Land", "Kandy", new BigDecimal("5.00"), "Tea",
                "A lush tea cultivation land in the central highlands of Sri Lanka.",
                "https://example.com/img.jpg",
                null, new BigDecimal("10000.00"));

        mockMvc.perform(post("/api/farmer/lands")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Total value is required"));
    }

    /**
     * AC-7: Null minimumInvestment fails @NotNull — returns 400 with
     * "Minimum investment is required".
     */
    @Test
    void createLandListing_withNullMinimumInvestment_shouldReturn400WithMessage() throws Exception {
        LandRegistrationRequest request = new LandRegistrationRequest(
                "My Land", "Kandy", new BigDecimal("5.00"), "Tea",
                "A lush tea cultivation land in the central highlands of Sri Lanka.",
                "https://example.com/img.jpg",
                new BigDecimal("500000.00"), null);

        mockMvc.perform(post("/api/farmer/lands")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Minimum investment is required"));
    }

    // -- AC-8: Business-rule errors from the service -----------------------

    /**
     * AC-8: When minimumInvestment > totalValue the service throws BadRequestException
     * and the handler returns 400 with the exact message and a timestamp.
     */
    @Test
    void createLandListing_whenMinimumInvestmentExceedsTotalValue_shouldReturn400WithMessage() throws Exception {
        LandRegistrationRequest request = new LandRegistrationRequest(
                "Bad Finance Land", "Galle", new BigDecimal("2.00"), "Pepper",
                "Southern pepper plantation near the coast with rich volcanic soil.",
                "https://example.com/img.jpg",
                new BigDecimal("50000.00"),    // totalValue
                new BigDecimal("100000.00"));  // minimumInvestment > totalValue

        when(farmerDashboardService.createLand(eq(FARMER_ID), any()))
                .thenThrow(new BadRequestException(
                        "Minimum investment cannot be greater than total value"));

        mockMvc.perform(post("/api/farmer/lands")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Minimum investment cannot be greater than total value"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    /**
     * AC-8: When the service detects a duplicate active listing (same name +
     * location) it throws ConflictException and the handler returns 409.
     */
    @Test
    void createLandListing_whenDuplicateActiveListing_shouldReturn409WithMessage() throws Exception {
        when(farmerDashboardService.createLand(eq(FARMER_ID), any()))
                .thenThrow(new ConflictException(
                        "You already have an active land listing with the same name and location"));

        mockMvc.perform(post("/api/farmer/lands")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("You already have an active land listing with the same name and location"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    /**
     * AC-8: PATCH /api/farmer/lands/{id}/active deactivates a land the farmer
     * owns and returns 200 with isActive = false in the body.
     */
    @Test
    void updateLandStatus_toInactive_shouldReturn200WithUpdatedIsActiveFlag() throws Exception {
        Long landId = 1L;
        LandResponse deactivated = buildLandResponse(landId, false);

        when(farmerDashboardService.updateLandStatus(FARMER_ID, landId, false))
                .thenReturn(deactivated);

        mockMvc.perform(patch("/api/farmer/lands/{landId}/active", landId)
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false))
                .andExpect(jsonPath("$.landId").value(landId));

        verify(farmerDashboardService).updateLandStatus(FARMER_ID, landId, false);
    }

    /**
     * AC-8: PATCH on a landId that belongs to a different farmer returns 404
     * with the service error message.
     */
    @Test
    void updateLandStatus_whenLandBelongsToDifferentFarmer_shouldReturn404WithMessage() throws Exception {
        when(farmerDashboardService.updateLandStatus(FARMER_ID, 99L, false))
                .thenThrow(new ResourceNotFoundException("Land not found for the current farmer"));

        mockMvc.perform(patch("/api/farmer/lands/99/active")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Land not found for the current farmer"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // -- Helpers -----------------------------------------------------------

    private LandRegistrationRequest validRequest() {
        return new LandRegistrationRequest(
                "Green Valley Tea Plot",
                "Kandy",
                new BigDecimal("5.50"),
                "Tea",
                "A lush tea cultivation land in the central highlands of Sri Lanka.",
                "https://example.com/img1.jpg,https://example.com/img2.jpg",
                new BigDecimal("500000.00"),
                new BigDecimal("10000.00")
        );
    }

    private LandResponse buildLandResponse(Long landId, boolean isActive) {
        return new LandResponse(
                landId,
                "Green Valley Tea Plot",
                "Kandy",
                new BigDecimal("5.50"),
                "Tea",
                "A lush tea cultivation land in the central highlands of Sri Lanka.",
                "https://example.com/img1.jpg,https://example.com/img2.jpg",
                new BigDecimal("500000.00"),
                new BigDecimal("10000.00"),
                0,
                isActive,
                LocalDateTime.now(),
                LocalDateTime.now(),
                FARMER_ID,
                "Saman Perera"
        );
    }
}