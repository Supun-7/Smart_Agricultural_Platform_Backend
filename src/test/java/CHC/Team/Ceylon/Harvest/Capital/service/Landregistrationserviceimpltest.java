package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.dto.farmer.LandRegistrationRequest;
import CHC.Team.Ceylon.Harvest.Capital.dto.farmer.LandResponse;
import CHC.Team.Ceylon.Harvest.Capital.entity.Land;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import CHC.Team.Ceylon.Harvest.Capital.exception.BadRequestException;
import CHC.Team.Ceylon.Harvest.Capital.exception.ConflictException;
import CHC.Team.Ceylon.Harvest.Capital.exception.ResourceNotFoundException;
import CHC.Team.Ceylon.Harvest.Capital.repository.FarmerApplicationRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.LandRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.ProjectRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Service-layer tests for CHC-275: Register Land & Display as Investment Listing.
 *
 * AC-3: Farmer can successfully submit the land registration form.
 * AC-4: Submitted land data is stored in the database.
 * AC-5: Registered land appears on the investment/product listing page.
 * AC-6: Only the logged-in farmer can create and manage their land listings.
 * AC-7: Invalid or incomplete submissions show appropriate validation errors.
 * AC-8: Duplicate prevention, image URL handling, ownership enforcement.
 */
@ExtendWith(MockitoExtension.class)
class LandRegistrationServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FarmerApplicationRepository farmerApplicationRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private MilestoneService milestoneService;

    @Mock
    private LandRepository landRepository;

    @InjectMocks
    private FarmerDashboardServiceImpl farmerDashboardService;

    private User farmer;

    private static final Long FARMER_ID = 10L;

    @BeforeEach
    void setUp() {
        farmer = new User();
        farmer.setUserId(FARMER_ID);
        farmer.setFullName("Saman Perera");
        farmer.setEmail("saman@farm.lk");
        farmer.setRole(Role.FARMER);
        farmer.setVerificationStatus(VerificationStatus.VERIFIED);
    }

    // -- AC-3 / AC-4: Successful land registration -------------------------

    /**
     * AC-3 / AC-4: createLand delegates to landRepository.save() and returns
     * a LandResponse whose fields exactly match the persisted entity.
     */
    @Test
    void createLand_withValidRequest_shouldSaveLandAndReturnCompleteResponse() {
        LandRegistrationRequest request = validRequest();
        Land saved = buildSavedLand(1L, request, farmer, true);

        given(userRepository.findById(FARMER_ID)).willReturn(Optional.of(farmer));
        given(landRepository.existsByFarmerUserUserIdAndProjectNameIgnoreCaseAndLocationIgnoreCaseAndIsActiveTrue(
                FARMER_ID, "Green Valley Tea Plot", "Kandy")).willReturn(false);
        given(landRepository.save(any(Land.class))).willReturn(saved);

        LandResponse response = farmerDashboardService.createLand(FARMER_ID, request);

        assertNotNull(response);
        assertEquals(1L,                          response.landId());
        assertEquals("Green Valley Tea Plot",     response.projectName());
        assertEquals("Kandy",                     response.location());
        assertEquals(new BigDecimal("5.50"),      response.sizeAcres());
        assertEquals("Tea",                       response.cropType());
        assertEquals(new BigDecimal("500000.00"), response.totalValue());
        assertEquals(new BigDecimal("10000.00"),  response.minimumInvestment());
        assertTrue(response.isActive());
        assertEquals(0,                           response.progressPercentage());
        assertEquals(FARMER_ID,                   response.farmerId());
        assertEquals("Saman Perera",              response.farmerName());

        verify(landRepository).save(any(Land.class));
    }

    /**
     * AC-4: The Land entity passed to landRepository.save() must have all fields
     * set from the request (whitespace trimmed) with correct defaults applied.
     */
    @Test
    void createLand_shouldPersistCorrectlyTrimmedEntityToRepository() {
        LandRegistrationRequest request = new LandRegistrationRequest(
                "  Spice Garden  ",
                "  Matale  ",
                new BigDecimal("3.00"),
                "  Cinnamon  ",
                "Prime cinnamon cultivation land with established trees and a drip-irrigation system.",
                "https://cdn.example.com/img1.jpg",
                new BigDecimal("750000.00"),
                new BigDecimal("25000.00")
        );
        Land saved = buildSavedLand(2L, validRequest(), farmer, true);

        given(userRepository.findById(FARMER_ID)).willReturn(Optional.of(farmer));
        given(landRepository.existsByFarmerUserUserIdAndProjectNameIgnoreCaseAndLocationIgnoreCaseAndIsActiveTrue(
                FARMER_ID, "Spice Garden", "Matale")).willReturn(false);
        given(landRepository.save(any(Land.class))).willReturn(saved);

        ArgumentCaptor<Land> captor = ArgumentCaptor.forClass(Land.class);
        farmerDashboardService.createLand(FARMER_ID, request);

        verify(landRepository).save(captor.capture());
        Land persisted = captor.getValue();

        assertEquals("Spice Garden", persisted.getProjectName(),        "Name should be trimmed");
        assertEquals("Matale",       persisted.getLocation(),           "Location should be trimmed");
        assertEquals("Cinnamon",     persisted.getCropType(),           "Crop type should be trimmed");
        assertTrue(persisted.getIsActive(),                             "New land must default to active");
        assertEquals(0,              persisted.getProgressPercentage(), "Progress must start at 0");
        assertEquals(farmer,         persisted.getFarmerUser(),         "FarmerUser must be linked");
    }

    // -- AC-5: Land listing retrieval --------------------------------------

    /**
     * AC-5: getFarmerLands returns all lands owned by the farmer, ordered by
     * creation date descending, reflecting what investors see in the listing page.
     */
    @Test
    void getFarmerLands_shouldReturnAllLandsOrderedByCreationDate() {
        LandRegistrationRequest req2 = new LandRegistrationRequest(
                "Pepper Field", "Galle", new BigDecimal("2.00"), "Pepper",
                "Southern pepper plantation near the coast with rich volcanic soil.",
                "https://example.com/p.jpg",
                new BigDecimal("200000.00"), new BigDecimal("5000.00"));

        Land land1 = buildSavedLand(1L, validRequest(), farmer, true);
        Land land2 = buildSavedLand(2L, req2,          farmer, true);

        given(userRepository.findById(FARMER_ID)).willReturn(Optional.of(farmer));
        given(landRepository.findByFarmerUserUserIdOrderByCreatedAtDesc(FARMER_ID))
                .willReturn(List.of(land1, land2));

        List<LandResponse> results = farmerDashboardService.getFarmerLands(FARMER_ID);

        assertNotNull(results);
        assertEquals(2,                       results.size());
        assertEquals(1L,                      results.get(0).landId());
        assertEquals("Green Valley Tea Plot", results.get(0).projectName());
        assertEquals(2L,                      results.get(1).landId());
        assertEquals("Pepper Field",          results.get(1).projectName());

        verify(landRepository).findByFarmerUserUserIdOrderByCreatedAtDesc(FARMER_ID);
    }

    /**
     * AC-5: getFarmerLands returns an empty list when the farmer has not yet
     * registered any lands.
     */
    @Test
    void getFarmerLands_whenNoLandsRegistered_shouldReturnEmptyList() {
        given(userRepository.findById(FARMER_ID)).willReturn(Optional.of(farmer));
        given(landRepository.findByFarmerUserUserIdOrderByCreatedAtDesc(FARMER_ID))
                .willReturn(List.of());

        List<LandResponse> results = farmerDashboardService.getFarmerLands(FARMER_ID);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    // -- AC-6: Role and ownership enforcement ------------------------------

    /**
     * AC-6: createLand throws BadRequestException when the resolved user is not
     * a FARMER — a non-farmer must never be allowed to register land.
     */
    @Test
    void createLand_whenUserIsNotFarmer_shouldThrowBadRequestException() {
        User investor = new User();
        investor.setUserId(99L);
        investor.setRole(Role.INVESTOR);
        investor.setVerificationStatus(VerificationStatus.VERIFIED);

        given(userRepository.findById(99L)).willReturn(Optional.of(investor));

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> farmerDashboardService.createLand(99L, validRequest()));

        assertTrue(ex.getMessage().contains("Only farmers can manage land listings"));
        verify(landRepository, never()).save(any());
    }

    /**
     * AC-6: createLand throws ResourceNotFoundException when the userId is unknown.
     */
    @Test
    void createLand_whenUserNotFound_shouldThrowResourceNotFoundException() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> farmerDashboardService.createLand(999L, validRequest()));

        assertTrue(ex.getMessage().contains("999"));
        verify(landRepository, never()).save(any());
    }

    /**
     * AC-6: updateLandStatus throws ResourceNotFoundException when the land
     * belongs to a different farmer — ownership must be strictly enforced.
     */
    @Test
    void updateLandStatus_whenLandBelongsToDifferentFarmer_shouldThrowResourceNotFoundException() {
        User otherFarmer = new User();
        otherFarmer.setUserId(99L);
        otherFarmer.setRole(Role.FARMER);
        otherFarmer.setVerificationStatus(VerificationStatus.VERIFIED);

        Land land = buildSavedLand(5L, validRequest(), otherFarmer, true);

        given(userRepository.findById(FARMER_ID)).willReturn(Optional.of(farmer));
        given(landRepository.findById(5L)).willReturn(Optional.of(land));

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> farmerDashboardService.updateLandStatus(FARMER_ID, 5L, false));

        assertTrue(ex.getMessage().contains("Land not found for the current farmer"));
        verify(landRepository, never()).save(any());
    }

    /**
     * AC-6 / AC-8: updateLandStatus throws ResourceNotFoundException when the
     * landId does not exist in the repository.
     */
    @Test
    void updateLandStatus_whenLandIdDoesNotExist_shouldThrowResourceNotFoundException() {
        given(userRepository.findById(FARMER_ID)).willReturn(Optional.of(farmer));
        given(landRepository.findById(999L)).willReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> farmerDashboardService.updateLandStatus(FARMER_ID, 999L, false));

        assertTrue(ex.getMessage().contains("999"));
    }

    // -- AC-7: Business-rule validation ------------------------------------

    /**
     * AC-7: createLand throws BadRequestException before touching the repository
     * when minimumInvestment exceeds totalValue.
     */
    @Test
    void createLand_whenMinimumInvestmentExceedsTotalValue_shouldThrowBadRequestException() {
        LandRegistrationRequest request = new LandRegistrationRequest(
                "Bad Finance Land", "Galle", new BigDecimal("2.00"), "Pepper",
                "Southern pepper plantation near the coast with rich volcanic soil.",
                "https://example.com/img.jpg",
                new BigDecimal("50000.00"),    // totalValue
                new BigDecimal("100000.00"));  // minimumInvestment > totalValue

        given(userRepository.findById(FARMER_ID)).willReturn(Optional.of(farmer));

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> farmerDashboardService.createLand(FARMER_ID, request));

        assertEquals("Minimum investment cannot be greater than total value", ex.getMessage());
        verify(landRepository, never()).save(any());
    }

    // -- AC-8: Duplicate prevention ----------------------------------------

    /**
     * AC-8: createLand throws ConflictException (before save) when an active
     * listing with the same projectName and location already exists.
     */
    @Test
    void createLand_whenDuplicateNameAndLocation_shouldThrowConflictException() {
        LandRegistrationRequest request = validRequest();

        given(userRepository.findById(FARMER_ID)).willReturn(Optional.of(farmer));
        given(landRepository.existsByFarmerUserUserIdAndProjectNameIgnoreCaseAndLocationIgnoreCaseAndIsActiveTrue(
                FARMER_ID, "Green Valley Tea Plot", "Kandy")).willReturn(true);

        ConflictException ex = assertThrows(
                ConflictException.class,
                () -> farmerDashboardService.createLand(FARMER_ID, request));

        assertTrue(ex.getMessage()
                .contains("You already have an active land listing with the same name and location"));
        verify(landRepository, never()).save(any());
    }

    /**
     * AC-8: The duplicate check is case-insensitive — the same name and location
     * typed in different casing must still trigger ConflictException.
     */
    @Test
    void createLand_withDuplicateNameAndLocationInDifferentCase_shouldThrowConflictException() {
        LandRegistrationRequest request = new LandRegistrationRequest(
                "green valley tea plot",  // lower-case version of an existing listing
                "KANDY",
                new BigDecimal("5.50"), "Tea",
                "A lush tea cultivation land in the central highlands of Sri Lanka.",
                "https://example.com/img.jpg",
                new BigDecimal("500000.00"), new BigDecimal("10000.00")
        );

        given(userRepository.findById(FARMER_ID)).willReturn(Optional.of(farmer));
        given(landRepository.existsByFarmerUserUserIdAndProjectNameIgnoreCaseAndLocationIgnoreCaseAndIsActiveTrue(
                FARMER_ID, "green valley tea plot", "KANDY")).willReturn(true);

        assertThrows(ConflictException.class,
                () -> farmerDashboardService.createLand(FARMER_ID, request));
        verify(landRepository, never()).save(any());
    }

    // -- AC-8: Image handling ----------------------------------------------

    /**
     * AC-8: Multiple comma-separated image URLs are stored as a single trimmed
     * string in the Land entity and returned intact in the LandResponse.
     */
    @Test
    void createLand_withMultipleImageUrls_shouldStoreAndReturnAllUrlsIntact() {
        String multipleImages =
                "https://cdn.example.com/img1.jpg,"
                        + "https://cdn.example.com/img2.jpg,"
                        + "https://cdn.example.com/img3.jpg";

        LandRegistrationRequest request = new LandRegistrationRequest(
                "Multi-Image Land", "Nuwara Eliya",
                new BigDecimal("4.00"), "Strawberry",
                "Strawberry farm in the cool highlands of Nuwara Eliya with drip irrigation.",
                multipleImages,
                new BigDecimal("300000.00"), new BigDecimal("15000.00")
        );

        Land saved = new Land();
        saved.setLandId(3L);
        saved.setFarmerUser(farmer);
        saved.setProjectName("Multi-Image Land");
        saved.setLocation("Nuwara Eliya");
        saved.setSizeAcres(new BigDecimal("4.00"));
        saved.setCropType("Strawberry");
        saved.setDescription("Strawberry farm in the cool highlands of Nuwara Eliya with drip irrigation.");
        saved.setImageUrls(multipleImages);
        saved.setTotalValue(new BigDecimal("300000.00"));
        saved.setMinimumInvestment(new BigDecimal("15000.00"));
        saved.setProgressPercentage(0);
        saved.setIsActive(true);
        saved.setCreatedAt(LocalDateTime.now());
        saved.setUpdatedAt(LocalDateTime.now());

        given(userRepository.findById(FARMER_ID)).willReturn(Optional.of(farmer));
        given(landRepository.existsByFarmerUserUserIdAndProjectNameIgnoreCaseAndLocationIgnoreCaseAndIsActiveTrue(
                FARMER_ID, "Multi-Image Land", "Nuwara Eliya")).willReturn(false);
        given(landRepository.save(any(Land.class))).willReturn(saved);

        LandResponse response = farmerDashboardService.createLand(FARMER_ID, request);

        assertEquals(multipleImages, response.imageUrls(),
                "All image URLs must be stored and returned as one comma-separated string");
    }

    // -- AC-8: Land status management --------------------------------------

    /**
     * AC-8: updateLandStatus deactivates a land owned by this farmer and
     * returns the updated LandResponse with isActive = false.
     */
    @Test
    void updateLandStatus_toInactive_shouldSaveAndReturnDeactivatedLand() {
        Land land        = buildSavedLand(1L, validRequest(), farmer, true);
        Land deactivated = buildSavedLand(1L, validRequest(), farmer, false);

        given(userRepository.findById(FARMER_ID)).willReturn(Optional.of(farmer));
        given(landRepository.findById(1L)).willReturn(Optional.of(land));
        given(landRepository.save(land)).willReturn(deactivated);

        LandResponse response = farmerDashboardService.updateLandStatus(FARMER_ID, 1L, false);

        assertFalse(response.isActive());
        verify(landRepository).save(land);
    }

    /**
     * AC-8: updateLandStatus re-activates a previously deactivated land and
     * returns the updated LandResponse with isActive = true.
     */
    @Test
    void updateLandStatus_toActive_shouldSaveAndReturnReactivatedLand() {
        Land land        = buildSavedLand(1L, validRequest(), farmer, false);
        Land reactivated = buildSavedLand(1L, validRequest(), farmer, true);

        given(userRepository.findById(FARMER_ID)).willReturn(Optional.of(farmer));
        given(landRepository.findById(1L)).willReturn(Optional.of(land));
        given(landRepository.save(land)).willReturn(reactivated);

        LandResponse response = farmerDashboardService.updateLandStatus(FARMER_ID, 1L, true);

        assertTrue(response.isActive());
        verify(landRepository).save(land);
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

    private Land buildSavedLand(Long landId, LandRegistrationRequest req,
                                User farmerUser, boolean isActive) {
        Land land = new Land();
        land.setLandId(landId);
        land.setFarmerUser(farmerUser);
        land.setProjectName(req.projectName().trim());
        land.setLocation(req.location().trim());
        land.setSizeAcres(req.sizeAcres());
        land.setCropType(req.cropType().trim());
        land.setDescription(req.description().trim());
        land.setImageUrls(req.imageUrls().trim());
        land.setTotalValue(req.totalValue());
        land.setMinimumInvestment(req.minimumInvestment());
        land.setProgressPercentage(0);
        land.setIsActive(isActive);
        land.setCreatedAt(LocalDateTime.now());
        land.setUpdatedAt(LocalDateTime.now());
        return land;
    }
}