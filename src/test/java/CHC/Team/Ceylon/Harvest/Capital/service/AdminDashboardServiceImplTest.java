package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.dto.AdminDashboardResponseDTO;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import CHC.Team.Ceylon.Harvest.Capital.exception.AdminDashboardException;
import CHC.Team.Ceylon.Harvest.Capital.repository.InvestmentRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

// AC-1: getDashboardData() aggregates data that populates GET /admin/dashboard
// AC-2: Farmers and investors are fetched by role and mapped to DTOs
// AC-3: Platform-wide investment total is computed via sumTotalInvestmentPlatformWide()
// AC-4: verificationStatus is mapped to the UserDTO status field per user
// AC-5: Any repository exception is wrapped in AdminDashboardException (HTTP 500)
@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private InvestmentRepository investmentRepository;

    @InjectMocks
    private AdminDashboardServiceImpl adminDashboardService;

    private User farmerVerified;
    private User farmerPending;
    private User investorVerified;
    private User investorNotSubmitted;

    @BeforeEach
    void setUp() {
        farmerVerified = buildUser(1L, "Nuwan Perera",      "nuwan@farm.lk",    Role.FARMER,   VerificationStatus.VERIFIED);
        farmerPending  = buildUser(2L, "Kasun Silva",       "kasun@farm.lk",    Role.FARMER,   VerificationStatus.PENDING);
        investorVerified      = buildUser(3L, "Sachith Fernando",  "sachith@invest.lk", Role.INVESTOR, VerificationStatus.VERIFIED);
        investorNotSubmitted  = buildUser(4L, "Rashmi Jayawardena","rashmi@invest.lk",  Role.INVESTOR, VerificationStatus.NOT_SUBMITTED);
    }

    // ── AC-1 + AC-2 + AC-3 + AC-4 ────────────────────────────────────────────
    @Test
    void getDashboardData_shouldReturnAggregatedFarmersInvestorsAndTotalInvestment() {
        given(userRepository.findByRole(Role.FARMER))
                .willReturn(List.of(farmerVerified, farmerPending));
        given(userRepository.findByRole(Role.INVESTOR))
                .willReturn(List.of(investorVerified, investorNotSubmitted));
        given(investmentRepository.sumTotalInvestmentPlatformWide())
                .willReturn(BigDecimal.valueOf(750000.00));

        AdminDashboardResponseDTO result = adminDashboardService.getDashboardData();

        // AC-2: counts
        assertEquals(2, result.getTotalFarmers());
        assertEquals(2, result.getTotalInvestors());

        // AC-2: farmer list
        assertEquals(2, result.getFarmers().size());
        assertEquals(1L,              result.getFarmers().get(0).getId());
        assertEquals("Nuwan Perera",  result.getFarmers().get(0).getName());
        assertEquals("nuwan@farm.lk", result.getFarmers().get(0).getEmail());
        assertEquals("FARMER",        result.getFarmers().get(0).getRole());
        assertEquals("Kasun Silva",   result.getFarmers().get(1).getName());

        // AC-2: investor list
        assertEquals(2, result.getInvestors().size());
        assertEquals(3L,                   result.getInvestors().get(0).getId());
        assertEquals("Sachith Fernando",   result.getInvestors().get(0).getName());
        assertEquals("sachith@invest.lk",  result.getInvestors().get(0).getEmail());
        assertEquals("INVESTOR",           result.getInvestors().get(0).getRole());
        assertEquals("Rashmi Jayawardena", result.getInvestors().get(1).getName());

        // AC-3: platform-wide investment total
        assertEquals(BigDecimal.valueOf(750000.00), result.getTotalInvestment());

        // AC-4: account statuses mapped from verificationStatus
        assertEquals("VERIFIED",      result.getFarmers().get(0).getStatus());
        assertEquals("PENDING",       result.getFarmers().get(1).getStatus());
        assertEquals("VERIFIED",      result.getInvestors().get(0).getStatus());
        assertEquals("NOT_SUBMITTED", result.getInvestors().get(1).getStatus());
    }

    // ── AC-3: Zero investment when no investments exist ───────────────────────
    @Test
    void getDashboardData_shouldReturnZeroTotalInvestmentWhenRepositoryReturnsZero() {
        given(userRepository.findByRole(Role.FARMER)).willReturn(List.of(farmerVerified));
        given(userRepository.findByRole(Role.INVESTOR)).willReturn(List.of());
        given(investmentRepository.sumTotalInvestmentPlatformWide())
                .willReturn(BigDecimal.ZERO);

        AdminDashboardResponseDTO result = adminDashboardService.getDashboardData();

        assertEquals(BigDecimal.ZERO, result.getTotalInvestment());
        assertEquals(1, result.getTotalFarmers());
        assertEquals(0, result.getTotalInvestors());
    }

    // ── AC-3: Null guard — service defaults to ZERO when repo returns null ────
    @Test
    void getDashboardData_shouldDefaultTotalInvestmentToZeroWhenRepositoryReturnsNull() {
        given(userRepository.findByRole(Role.FARMER)).willReturn(List.of());
        given(userRepository.findByRole(Role.INVESTOR)).willReturn(List.of());
        given(investmentRepository.sumTotalInvestmentPlatformWide()).willReturn(null);

        AdminDashboardResponseDTO result = adminDashboardService.getDashboardData();

        assertEquals(BigDecimal.ZERO, result.getTotalInvestment(),
                "totalInvestment should default to ZERO when repository returns null");
    }

    // ── AC-2: Empty platform — no farmers, no investors ───────────────────────
    @Test
    void getDashboardData_shouldReturnEmptyListsWhenNoPlatformUsersExist() {
        given(userRepository.findByRole(Role.FARMER)).willReturn(List.of());
        given(userRepository.findByRole(Role.INVESTOR)).willReturn(List.of());
        given(investmentRepository.sumTotalInvestmentPlatformWide())
                .willReturn(BigDecimal.ZERO);

        AdminDashboardResponseDTO result = adminDashboardService.getDashboardData();

        assertEquals(0, result.getTotalFarmers());
        assertEquals(0, result.getTotalInvestors());
        assertTrue(result.getFarmers().isEmpty());
        assertTrue(result.getInvestors().isEmpty());
    }

    // ── AC-4: REJECTED status is correctly mapped to UserDTO ─────────────────
    @Test
    void getDashboardData_shouldMapRejectedVerificationStatusToUserDtoStatus() {
        User rejectedInvestor = buildUser(
                5L, "Rejected Investor", "rejected@invest.lk",
                Role.INVESTOR, VerificationStatus.REJECTED);

        given(userRepository.findByRole(Role.FARMER)).willReturn(List.of());
        given(userRepository.findByRole(Role.INVESTOR)).willReturn(List.of(rejectedInvestor));
        given(investmentRepository.sumTotalInvestmentPlatformWide())
                .willReturn(BigDecimal.ZERO);

        AdminDashboardResponseDTO result = adminDashboardService.getDashboardData();

        assertEquals("REJECTED", result.getInvestors().get(0).getStatus());
    }

    // ── AC-5: Repository exception is wrapped in AdminDashboardException ──────
    @Test
    void getDashboardData_shouldThrowAdminDashboardExceptionWhenRepositoryFails() {
        given(userRepository.findByRole(Role.FARMER))
                .willThrow(new RuntimeException("DB connection lost"));

        AdminDashboardException exception = assertThrows(
                AdminDashboardException.class,
                () -> adminDashboardService.getDashboardData());

        assertTrue(exception.getMessage().contains("Unable to load admin dashboard"),
                "Exception message should indicate dashboard load failure");
    }

    // ── AC-3: Large investment total is preserved accurately ─────────────────
    @Test
    void getDashboardData_shouldAccuratelyReportLargePlatformWideTotalInvestment() {
        given(userRepository.findByRole(Role.FARMER))
                .willReturn(List.of(farmerVerified, farmerPending));
        given(userRepository.findByRole(Role.INVESTOR))
                .willReturn(List.of(investorVerified, investorNotSubmitted));
        given(investmentRepository.sumTotalInvestmentPlatformWide())
                .willReturn(BigDecimal.valueOf(12_500_000.00));

        AdminDashboardResponseDTO result = adminDashboardService.getDashboardData();

        assertEquals(BigDecimal.valueOf(12_500_000.00), result.getTotalInvestment());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private User buildUser(Long id, String fullName, String email,
                           Role role, VerificationStatus status) {
        User user = new User();
        user.setUserId(id);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setRole(role);
        user.setVerificationStatus(status);
        return user;
    }
}
