package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.entity.FarmerApplication;
import CHC.Team.Ceylon.Harvest.Capital.entity.KycSubmission;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import CHC.Team.Ceylon.Harvest.Capital.repository.FarmerApplicationRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.KycSubmissionRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// AC-1: Dashboard endpoint is reachable and returns structured data
// AC-2: pendingFarmers list includes farmerName, farmLocation (project), and submittedAt
// AC-3: pendingKyc investor activity summary is present
// AC-4: Successful 200 response (happy path; error state tested in AuditorAccessControlWebMvcTest)
// AC-5: Only PENDING farmer applications appear in the pendingFarmers list
@ExtendWith(MockitoExtension.class)
class AuditorDashboardControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FarmerApplicationRepository farmerApplicationRepository;

    @Mock
    private KycSubmissionRepository kycSubmissionRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuditorController auditorController = new AuditorController(
                userRepository,
                farmerApplicationRepository,
                kycSubmissionRepository);

        mockMvc = MockMvcBuilders.standaloneSetup(auditorController).build();
    }

    // ── AC-1 + AC-2 + AC-3 ───────────────────────────────────────────────────
    @Test
    void getDashboard_shouldReturnPendingFarmersAndKycActivityData() throws Exception {
        // Build a user whose email appears on both farmer and KYC entries
        User farmerUser = buildUser(10L, "Nuwan Perera", "nuwan@farm.lk");
        User investorUser = buildUser(20L, "Sachith Fernando", "sachith@invest.lk");

        // AC-2: pending farmer application with farmer name, project (farmLocation), submittedAt
        FarmerApplication pendingFarmer = buildFarmerApplication(
                "fa-uuid-001",
                farmerUser,
                "Nuwan",
                "Perera",
                "Kandy Tea Plantation",
                BigDecimal.valueOf(5.50),
                "Tea",
                LocalDateTime.of(2026, 3, 10, 9, 0),
                VerificationStatus.PENDING);

        // AC-3: pending KYC entry representing investor activity
        KycSubmission pendingKyc = buildKycSubmission(
                "kyc-uuid-001",
                investorUser,
                "Sachith",
                "Fernando",
                "Sri Lankan",
                "NIC",
                "199912345678",
                LocalDateTime.of(2026, 3, 12, 14, 30),
                VerificationStatus.PENDING);

        // AC-5: repository returns only PENDING records
        when(farmerApplicationRepository.findByStatus(VerificationStatus.PENDING))
                .thenReturn(List.of(pendingFarmer));
        when(kycSubmissionRepository.findByStatus(VerificationStatus.PENDING))
                .thenReturn(List.of(pendingKyc));

        // AC-1: GET /api/auditor/dashboard succeeds
        mockMvc.perform(get("/api/auditor/dashboard"))
                .andExpect(status().isOk())

                // AC-3: investor activity (KYC) summary fields
                .andExpect(jsonPath("$.kycCount").value(1))
                .andExpect(jsonPath("$.pendingKyc.length()").value(1))
                .andExpect(jsonPath("$.pendingKyc[0].fullName").value("Sachith Fernando"))
                .andExpect(jsonPath("$.pendingKyc[0].email").value("sachith@invest.lk"))
                .andExpect(jsonPath("$.pendingKyc[0].nationality").value("Sri Lankan"))
                .andExpect(jsonPath("$.pendingKyc[0].idType").value("NIC"))
                .andExpect(jsonPath("$.pendingKyc[0].idNumber").value("199912345678"))
                .andExpect(jsonPath("$.pendingKyc[0].status").value("PENDING"))

                // AC-2: pending milestone (farmer) list fields
                .andExpect(jsonPath("$.farmerCount").value(1))
                .andExpect(jsonPath("$.pendingFarmers.length()").value(1))
                .andExpect(jsonPath("$.pendingFarmers[0].farmerName").value("Nuwan"))
                .andExpect(jsonPath("$.pendingFarmers[0].surname").value("Perera"))
                .andExpect(jsonPath("$.pendingFarmers[0].email").value("nuwan@farm.lk"))
                .andExpect(jsonPath("$.pendingFarmers[0].farmLocation").value("Kandy Tea Plantation"))
                .andExpect(jsonPath("$.pendingFarmers[0].status").value("PENDING"));
    }

    // ── AC-4: Empty dashboard when no pending items exist ────────────────────
    @Test
    void getDashboard_shouldReturnEmptyListsWhenNoPendingItems() throws Exception {
        when(farmerApplicationRepository.findByStatus(VerificationStatus.PENDING))
                .thenReturn(List.of());
        when(kycSubmissionRepository.findByStatus(VerificationStatus.PENDING))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/auditor/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingKyc.length()").value(0))
                .andExpect(jsonPath("$.pendingFarmers.length()").value(0))
                .andExpect(jsonPath("$.kycCount").value(0))
                .andExpect(jsonPath("$.farmerCount").value(0));
    }

    // ── AC-5: Only PENDING milestones appear — VERIFIED entry is absent ───────
    @Test
    void getDashboard_shouldExcludeNonPendingFarmerApplications() throws Exception {
        User farmerUser = buildUser(30L, "Dilshan Farmer", "dilshan@farm.lk");

        FarmerApplication pendingOnly = buildFarmerApplication(
                "fa-uuid-pending",
                farmerUser,
                "Dilshan",
                "Farmer",
                "Galle Coconut Estate",
                BigDecimal.valueOf(3.00),
                "Coconut",
                LocalDateTime.of(2026, 3, 15, 8, 0),
                VerificationStatus.PENDING);

        // Repository is stubbed to return only PENDING — VERIFIED/REJECTED are excluded by the query
        when(farmerApplicationRepository.findByStatus(VerificationStatus.PENDING))
                .thenReturn(List.of(pendingOnly));
        when(kycSubmissionRepository.findByStatus(VerificationStatus.PENDING))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/auditor/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farmerCount").value(1))
                .andExpect(jsonPath("$.pendingFarmers[0].status").value("PENDING"))
                // Ensure only one record is present (no VERIFIED/REJECTED leaking in)
                .andExpect(jsonPath("$.pendingFarmers.length()").value(1));
    }

    // ── AC-2 + AC-3: Multiple pending items from both queues ─────────────────
    @Test
    void getDashboard_shouldReturnMultiplePendingFarmersAndKycEntries() throws Exception {
        User user1 = buildUser(11L, "Kasun Silva", "kasun@farm.lk");
        User user2 = buildUser(12L, "Amali Perera", "amali@farm.lk");
        User investor1 = buildUser(21L, "Rashmi Investor", "rashmi@invest.lk");
        User investor2 = buildUser(22L, "Chanaka Investor", "chanaka@invest.lk");

        FarmerApplication farmer1 = buildFarmerApplication(
                "fa-001", user1, "Kasun", "Silva",
                "Matara Paddy Field", BigDecimal.valueOf(8.0), "Paddy",
                LocalDateTime.of(2026, 3, 1, 10, 0), VerificationStatus.PENDING);

        FarmerApplication farmer2 = buildFarmerApplication(
                "fa-002", user2, "Amali", "Perera",
                "Kurunegala Rubber Estate", BigDecimal.valueOf(12.5), "Rubber",
                LocalDateTime.of(2026, 3, 5, 11, 0), VerificationStatus.PENDING);

        KycSubmission kyc1 = buildKycSubmission(
                "kyc-001", investor1, "Rashmi", "Investor",
                "Sri Lankan", "PASSPORT", "N1234567",
                LocalDateTime.of(2026, 3, 8, 9, 0), VerificationStatus.PENDING);

        KycSubmission kyc2 = buildKycSubmission(
                "kyc-002", investor2, "Chanaka", "Investor",
                "Sri Lankan", "NIC", "200056789012",
                LocalDateTime.of(2026, 3, 11, 16, 0), VerificationStatus.PENDING);

        when(farmerApplicationRepository.findByStatus(VerificationStatus.PENDING))
                .thenReturn(List.of(farmer1, farmer2));
        when(kycSubmissionRepository.findByStatus(VerificationStatus.PENDING))
                .thenReturn(List.of(kyc1, kyc2));

        mockMvc.perform(get("/api/auditor/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farmerCount").value(2))
                .andExpect(jsonPath("$.kycCount").value(2))
                .andExpect(jsonPath("$.pendingFarmers.length()").value(2))
                .andExpect(jsonPath("$.pendingKyc.length()").value(2))
                .andExpect(jsonPath("$.pendingFarmers[0].farmerName").value("Kasun"))
                .andExpect(jsonPath("$.pendingFarmers[1].farmLocation").value("Kurunegala Rubber Estate"))
                .andExpect(jsonPath("$.pendingKyc[0].fullName").value("Rashmi Investor"))
                .andExpect(jsonPath("$.pendingKyc[1].idType").value("NIC"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User buildUser(Long id, String fullName, String email) {
        User user = new User();
        user.setUserId(id);
        user.setFullName(fullName);
        user.setEmail(email);
        return user;
    }

    private FarmerApplication buildFarmerApplication(
            String id, User user, String farmerName, String surname,
            String farmLocation, BigDecimal landSizeAcres, String cropTypes,
            LocalDateTime submittedAt, VerificationStatus status) {
        FarmerApplication app = new FarmerApplication();
        app.setId(id);
        app.setUser(user);
        app.setFarmerName(farmerName);
        app.setSurname(surname);
        app.setFarmLocation(farmLocation);
        app.setLandSizeAcres(landSizeAcres);
        app.setCropTypes(cropTypes);
        app.setSubmittedAt(submittedAt);
        app.setStatus(status);
        return app;
    }

    private KycSubmission buildKycSubmission(
            String id, User user, String firstName, String lastName,
            String nationality, String idType, String idNumber,
            LocalDateTime submittedAt, VerificationStatus status) {
        KycSubmission kyc = new KycSubmission();
        kyc.setId(id);
        kyc.setUser(user);
        kyc.setFirstName(firstName);
        kyc.setLastName(lastName);
        kyc.setNationality(nationality);
        kyc.setIdType(idType);
        kyc.setIdNumber(idNumber);
        kyc.setSubmittedAt(submittedAt);
        kyc.setStatus(status);
        return kyc;
    }
}
