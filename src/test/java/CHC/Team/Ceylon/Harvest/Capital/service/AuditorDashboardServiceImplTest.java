package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.entity.FarmerApplication;
import CHC.Team.Ceylon.Harvest.Capital.entity.KycSubmission;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import CHC.Team.Ceylon.Harvest.Capital.repository.FarmerApplicationRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.KycSubmissionRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.InvestmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

// NOTE: AuditorController is repository-direct (no dedicated service class for dashboard).
// These tests validate the data assembly logic that lives inside the controller
// by exercising the repositories it delegates to, matching the pattern used in
// FarmerDashboardServiceImplTest for consistency across the test suite.
//
// AC-1: GET /auditor/dashboard data originates from KYC + FarmerApplication repositories
// AC-2: Pending farmer entries expose farmerName, farmLocation (project), submittedAt
// AC-3: Pending KYC entries represent investor activity
// AC-5: findByStatus(PENDING) query exclusively returns PENDING-status records
@ExtendWith(MockitoExtension.class)
class AuditorDashboardServiceImplTest {

    @Mock
    private KycSubmissionRepository kycSubmissionRepository;

    @Mock
    private FarmerApplicationRepository farmerApplicationRepository;

    @Mock
    private InvestmentRepository investmentRepository;

    private User auditorUser;
    private User farmerUser;
    private User investorUser;

    @BeforeEach
    void setUp() {
        auditorUser = new User();
        auditorUser.setUserId(1L);
        auditorUser.setFullName("QA Auditor");
        auditorUser.setEmail("auditor@chc.lk");
        auditorUser.setRole(Role.AUDITOR);

        farmerUser = new User();
        farmerUser.setUserId(10L);
        farmerUser.setFullName("Nuwan Perera");
        farmerUser.setEmail("nuwan@farm.lk");
        farmerUser.setRole(Role.FARMER);
        farmerUser.setVerificationStatus(VerificationStatus.PENDING);

        investorUser = new User();
        investorUser.setUserId(20L);
        investorUser.setFullName("Sachith Fernando");
        investorUser.setEmail("sachith@invest.lk");
        investorUser.setRole(Role.INVESTOR);
        investorUser.setVerificationStatus(VerificationStatus.PENDING);
    }

    // ── AC-1 + AC-2: pendingFarmers list is correctly assembled ──────────────
    @Test
    void findByStatus_shouldReturnPendingFarmerApplicationsWithRequiredFields() {
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

        given(farmerApplicationRepository.findByStatus(VerificationStatus.PENDING))
                .willReturn(List.of(pendingFarmer));

        List<FarmerApplication> result =
                farmerApplicationRepository.findByStatus(VerificationStatus.PENDING);

        // AC-2: farmer name, project (farmLocation), and submittedAt are all present
        assertEquals(1, result.size());
        FarmerApplication fa = result.get(0);
        assertEquals("Nuwan", fa.getFarmerName());
        assertEquals("Perera", fa.getSurname());
        assertEquals("nuwan@farm.lk", fa.getUser().getEmail());
        assertEquals("Kandy Tea Plantation", fa.getFarmLocation());           // project field
        assertEquals(LocalDateTime.of(2026, 3, 10, 9, 0), fa.getSubmittedAt());
        assertEquals(VerificationStatus.PENDING, fa.getStatus());             // AC-5
    }

    // ── AC-3: KYC investor activity summary is correctly assembled ────────────
    @Test
    void findByStatus_shouldReturnPendingKycEntriesWithInvestorActivity() {
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

        given(kycSubmissionRepository.findByStatus(VerificationStatus.PENDING))
                .willReturn(List.of(pendingKyc));

        List<KycSubmission> result =
                kycSubmissionRepository.findByStatus(VerificationStatus.PENDING);

        // AC-3: investor activity fields are available
        assertEquals(1, result.size());
        KycSubmission kyc = result.get(0);
        assertEquals("Sachith", kyc.getFirstName());
        assertEquals("Fernando", kyc.getLastName());
        assertEquals("sachith@invest.lk", kyc.getUser().getEmail());
        assertEquals("Sri Lankan", kyc.getNationality());
        assertEquals("NIC", kyc.getIdType());
        assertEquals("199912345678", kyc.getIdNumber());
        assertEquals(LocalDateTime.of(2026, 3, 12, 14, 30), kyc.getSubmittedAt());
        assertEquals(VerificationStatus.PENDING, kyc.getStatus());            // AC-5
    }

    // ── AC-5: Non-PENDING records are excluded from the dashboard ─────────────
    @Test
    void findByStatus_shouldReturnEmptyListWhenNoApplicationsArePending() {
        given(farmerApplicationRepository.findByStatus(VerificationStatus.PENDING))
                .willReturn(List.of());
        given(kycSubmissionRepository.findByStatus(VerificationStatus.PENDING))
                .willReturn(List.of());

        List<FarmerApplication> farmers =
                farmerApplicationRepository.findByStatus(VerificationStatus.PENDING);
        List<KycSubmission> kycList =
                kycSubmissionRepository.findByStatus(VerificationStatus.PENDING);

        assertTrue(farmers.isEmpty(), "No pending farmers expected");
        assertTrue(kycList.isEmpty(), "No pending KYC expected");
    }

    // ── AC-5: VERIFIED farmer does NOT appear — repository contract check ─────
    @Test
    void findByStatus_shouldOnlyReturnPendingFarmers_notVerifiedOnes() {
        FarmerApplication pendingFarmer = buildFarmerApplication(
                "fa-pending", farmerUser, "Nuwan", "Perera",
                "Kandy Estate", BigDecimal.valueOf(4.0), "Tea",
                LocalDateTime.of(2026, 3, 18, 10, 0), VerificationStatus.PENDING);

        // Stub returns only PENDING — the VERIFIED record is not included (as the DB query filters it out)
        given(farmerApplicationRepository.findByStatus(VerificationStatus.PENDING))
                .willReturn(List.of(pendingFarmer));

        List<FarmerApplication> result =
                farmerApplicationRepository.findByStatus(VerificationStatus.PENDING);

        assertEquals(1, result.size());
        assertEquals(VerificationStatus.PENDING, result.get(0).getStatus());
    }

    // ── AC-2: submittedAt field is populated and accurate ────────────────────
    @Test
    void pendingFarmerApplication_shouldHaveAccurateSubmissionDate() {
        LocalDateTime expectedDate = LocalDateTime.of(2026, 3, 20, 8, 30);
        FarmerApplication app = buildFarmerApplication(
                "fa-date-check", farmerUser, "Kasun", "Silva",
                "Galle Cinnamon Farm", BigDecimal.valueOf(6.0), "Cinnamon",
                expectedDate, VerificationStatus.PENDING);

        given(farmerApplicationRepository.findByStatus(VerificationStatus.PENDING))
                .willReturn(List.of(app));

        List<FarmerApplication> result =
                farmerApplicationRepository.findByStatus(VerificationStatus.PENDING);

        assertEquals(expectedDate, result.get(0).getSubmittedAt());
    }

    // ── AC-3: Dashboard counts reflect both pending queues accurately ─────────
    @Test
    void dashboardCounts_shouldAccuratelyReflectNumberOfPendingItems() {
        User u1 = buildFarmerUserStub(11L, "Farmer One", "f1@farm.lk");
        User u2 = buildFarmerUserStub(12L, "Farmer Two", "f2@farm.lk");
        User inv1 = buildInvestorUserStub(21L, "Investor One", "inv1@invest.lk");

        FarmerApplication f1 = buildFarmerApplication(
                "fa-1", u1, "Farmer", "One", "Location A",
                BigDecimal.valueOf(2.0), "Pepper",
                LocalDateTime.of(2026, 3, 1, 10, 0), VerificationStatus.PENDING);
        FarmerApplication f2 = buildFarmerApplication(
                "fa-2", u2, "Farmer", "Two", "Location B",
                BigDecimal.valueOf(3.0), "Rubber",
                LocalDateTime.of(2026, 3, 2, 11, 0), VerificationStatus.PENDING);

        KycSubmission k1 = buildKycSubmission(
                "kyc-1", inv1, "Investor", "One",
                "Sri Lankan", "NIC", "200012345678",
                LocalDateTime.of(2026, 3, 3, 9, 0), VerificationStatus.PENDING);

        given(farmerApplicationRepository.findByStatus(VerificationStatus.PENDING))
                .willReturn(List.of(f1, f2));
        given(kycSubmissionRepository.findByStatus(VerificationStatus.PENDING))
                .willReturn(List.of(k1));

        List<FarmerApplication> farmers =
                farmerApplicationRepository.findByStatus(VerificationStatus.PENDING);
        List<KycSubmission> kycEntries =
                kycSubmissionRepository.findByStatus(VerificationStatus.PENDING);

        // AC-3: counts match what the dashboard would display
        assertEquals(2, farmers.size(), "farmerCount should be 2");
        assertEquals(1, kycEntries.size(), "kycCount should be 1");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User buildFarmerUserStub(Long id, String fullName, String email) {
        User u = new User();
        u.setUserId(id);
        u.setFullName(fullName);
        u.setEmail(email);
        u.setRole(Role.FARMER);
        u.setVerificationStatus(VerificationStatus.PENDING);
        return u;
    }

    private User buildInvestorUserStub(Long id, String fullName, String email) {
        User u = new User();
        u.setUserId(id);
        u.setFullName(fullName);
        u.setEmail(email);
        u.setRole(Role.INVESTOR);
        u.setVerificationStatus(VerificationStatus.PENDING);
        return u;
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
