package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.entity.Investment;
import CHC.Team.Ceylon.Harvest.Capital.entity.Land;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.entity.Wallet;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.exception.ResourceNotFoundException;
import CHC.Team.Ceylon.Harvest.Capital.repository.InvestmentRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.KycSubmissionRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.LandRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.WalletRepository;
import CHC.Team.Ceylon.Harvest.Capital.service.impl.InvestorDashboardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

// AC-1  Available (unfunded) lands are returned from the database — getOpportunities()
// AC-3  Wallet balance is validated before processing — validateInvestorBalance()
// AC-4  Insufficient balance throws an exception and blocks the investment
// AC-6  walletBalance in dashboard reflects the reduced post-investment balance from DB
// AC-8  Invested lands appear on the investor dashboard — getDashboard() / getPortfolio()
@ExtendWith(MockitoExtension.class)
class InvestmentServiceImplTest {

    @Mock private UserRepository        userRepository;
    @Mock private WalletRepository      walletRepository;
    @Mock private InvestmentRepository  investmentRepository;
    @Mock private KycSubmissionRepository kycSubmissionRepository;
    @Mock private LandRepository        landRepository;

    @InjectMocks
    private InvestorDashboardServiceImpl investorDashboardService;

    private User   investor;
    private Wallet wallet;
    private Land   activeLand;
    private Land   secondLand;

    @BeforeEach
    void setUp() {
        investor = new User();
        investor.setUserId(10L);
        investor.setFullName("Sachith Investor");
        investor.setEmail("sachith@invest.lk");
        investor.setRole(Role.INVESTOR);

        wallet = new Wallet();
        wallet.setWalletId(200L);
        wallet.setUser(investor);
        wallet.setBalance(BigDecimal.valueOf(400000.00)); // post-investment balance
        wallet.setCurrency("LKR");

        activeLand = new Land();
        activeLand.setLandId(101L);
        activeLand.setProjectName("Pepper Estate Phase 1");
        activeLand.setLocation("Kandy");
        activeLand.setTotalValue(BigDecimal.valueOf(800000.00));
        activeLand.setMinimumInvestment(BigDecimal.valueOf(25000.00));
        activeLand.setProgressPercentage(0);
        activeLand.setIsActive(true);

        secondLand = new Land();
        secondLand.setLandId(102L);
        secondLand.setProjectName("Tea Revival Project");
        secondLand.setLocation("Nuwara Eliya");
        secondLand.setTotalValue(BigDecimal.valueOf(400000.00));
        secondLand.setMinimumInvestment(BigDecimal.valueOf(15000.00));
        secondLand.setProgressPercentage(0);
        secondLand.setIsActive(true);
    }

    // ── AC-1: getOpportunities() returns only active (unfunded) lands from DB ──────────────
    @Test
    void getOpportunities_shouldReturnOnlyActiveLandsOrderedByCreatedAt() {
        given(landRepository.findByIsActiveTrueOrderByCreatedAtDesc())
                .willReturn(List.of(secondLand, activeLand));

        Map<String, Object> result = investorDashboardService.getOpportunities();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> opportunities =
                (List<Map<String, Object>>) result.get("opportunities");

        assertEquals(2, result.get("total"));
        assertEquals(2, opportunities.size());

        // verify fields returned for each opportunity
        assertEquals(102L,                          opportunities.get(0).get("landId"));
        assertEquals("Tea Revival Project",          opportunities.get(0).get("projectName"));
        assertEquals("Nuwara Eliya",                 opportunities.get(0).get("location"));
        assertEquals(BigDecimal.valueOf(400000.00),  opportunities.get(0).get("totalValue"));
        assertEquals(BigDecimal.valueOf(15000.00),   opportunities.get(0).get("minimumInvestment"));

        assertEquals(101L,                           opportunities.get(1).get("landId"));
        assertEquals("Pepper Estate Phase 1",        opportunities.get(1).get("projectName"));
    }

    // ── AC-1: No active lands returns empty opportunity list ────────────────────────────────
    @Test
    void getOpportunities_whenNoActiveLandsExist_shouldReturnEmptyList() {
        given(landRepository.findByIsActiveTrueOrderByCreatedAtDesc()).willReturn(List.of());

        Map<String, Object> result = investorDashboardService.getOpportunities();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> opportunities =
                (List<Map<String, Object>>) result.get("opportunities");

        assertEquals(0, result.get("total"));
        assertTrue(opportunities.isEmpty());
    }

    // ── AC-3 + AC-4: validateInvestorBalance() passes when wallet has enough funds ────────
    @Test
    void validateInvestorBalance_withSufficientBalance_shouldNotThrow() {
        wallet.setBalance(BigDecimal.valueOf(500000.00));
        given(walletRepository.findByUserUserId(10L)).willReturn(Optional.of(wallet));

        assertDoesNotThrow(
                () -> investorDashboardService.validateInvestorBalance(10L, 100000L));
    }

    // ── AC-3 + AC-4: validateInvestorBalance() throws when wallet is short ──────────────
    @Test
    void validateInvestorBalance_withInsufficientBalance_shouldThrowException() {
        wallet.setBalance(BigDecimal.valueOf(10000.00));
        given(walletRepository.findByUserUserId(10L)).willReturn(Optional.of(wallet));

        Exception exception = assertThrows(
                Exception.class,
                () -> investorDashboardService.validateInvestorBalance(10L, 100000L));

        assertTrue(exception.getMessage().contains("Insufficient wallet balance"));
    }

    // ── AC-4: validateInvestorBalance() throws when wallet is missing ───────────────────
    @Test
    void validateInvestorBalance_whenWalletNotFound_shouldThrowResourceNotFoundException() {
        given(walletRepository.findByUserUserId(10L)).willReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> investorDashboardService.validateInvestorBalance(10L, 50000L));

        assertTrue(exception.getMessage().contains("Wallet not found for investor: 10"));
    }

    // ── AC-6 + AC-8: getDashboard() returns post-investment wallet balance from DB ──────
    @Test
    void getDashboard_shouldReflectReducedWalletBalanceAfterInvestment() {
        // wallet.balance is 400 000 — what remains after the 100 000 investment
        Investment inv = buildInvestment(
                501L, BigDecimal.valueOf(100000.00),
                Investment.InvestmentStatus.ACTIVE,
                LocalDateTime.of(2026, 3, 10, 9, 0),
                activeLand);

        given(userRepository.findById(10L)).willReturn(Optional.of(investor));
        given(walletRepository.findByUserUserId(10L)).willReturn(Optional.of(wallet));
        given(kycSubmissionRepository.findTopByUserUserIdOrderBySubmittedAtDesc(10L))
                .willReturn(Optional.empty());
        given(investmentRepository.findAllByUserIdWithLand(10L)).willReturn(List.of(inv));
        given(investmentRepository.sumTotalByUserId(10L))
                .willReturn(BigDecimal.valueOf(100000.00));
        given(investmentRepository.sumByUserIdAndStatus(10L, Investment.InvestmentStatus.ACTIVE))
                .willReturn(BigDecimal.valueOf(100000.00));
        given(investmentRepository.sumByUserIdAndStatus(10L, Investment.InvestmentStatus.PENDING))
                .willReturn(BigDecimal.ZERO);
        given(investmentRepository.sumByUserIdAndStatus(10L, Investment.InvestmentStatus.COMPLETED))
                .willReturn(BigDecimal.ZERO);
        given(investmentRepository.countDistinctLandsByUserId(10L)).willReturn(1L);
        given(investmentRepository.countActiveLandsByUserId(10L)).willReturn(1L);

        Map<String, Object> result = investorDashboardService.getDashboard(10L);

        // AC-6: wallet balance reflects the post-investment value from DB
        assertEquals(BigDecimal.valueOf(400000.00), result.get("walletBalance"));
        assertEquals("LKR", result.get("currency"));

        // AC-8: the invested land appears in the dashboard
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> investedLands =
                (List<Map<String, Object>>) result.get("investedLands");

        assertEquals(1, investedLands.size());
        assertEquals(501L,                          investedLands.get(0).get("investmentId"));
        assertEquals(101L,                          investedLands.get(0).get("landId"));
        assertEquals("Pepper Estate Phase 1",        investedLands.get(0).get("projectName"));
        assertEquals("Kandy",                        investedLands.get(0).get("location"));
        assertEquals(BigDecimal.valueOf(100000.00),  investedLands.get(0).get("amountInvested"));
        assertEquals("ACTIVE",                       investedLands.get(0).get("status"));
    }

    // ── AC-8: getPortfolio() includes the newly funded land ────────────────────────────────
    @Test
    void getPortfolio_shouldIncludeInvestedLandWithCorrectBreakdown() {
        Investment inv = buildInvestment(
                501L, BigDecimal.valueOf(100000.00),
                Investment.InvestmentStatus.ACTIVE,
                LocalDateTime.of(2026, 3, 10, 9, 0),
                activeLand);

        given(userRepository.findById(10L)).willReturn(Optional.of(investor));
        given(investmentRepository.findAllByUserIdWithLand(10L)).willReturn(List.of(inv));
        given(investmentRepository.sumTotalByUserId(10L))
                .willReturn(BigDecimal.valueOf(100000.00));
        given(investmentRepository.sumByUserIdAndStatus(10L, Investment.InvestmentStatus.ACTIVE))
                .willReturn(BigDecimal.valueOf(100000.00));

        Map<String, Object> result = investorDashboardService.getPortfolio(10L);

        assertEquals(BigDecimal.valueOf(100000.00), result.get("totalInvested"));
        assertEquals(BigDecimal.valueOf(100000.00), result.get("activeAmount"));
        assertEquals(1,                             result.get("count"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> investments =
                (List<Map<String, Object>>) result.get("investments");

        assertEquals(1, investments.size());
        assertEquals("Pepper Estate Phase 1", investments.get(0).get("projectName"));
        assertEquals("ACTIVE",                investments.get(0).get("status"));
    }

    // ── AC-8: getPortfolio() with multiple investments shows all lands ──────────────────
    @Test
    void getPortfolio_withMultipleInvestments_shouldListAllFundedLands() {
        Investment inv1 = buildInvestment(
                501L, BigDecimal.valueOf(100000.00),
                Investment.InvestmentStatus.ACTIVE,
                LocalDateTime.of(2026, 3, 1, 10, 0),
                activeLand);

        Investment inv2 = buildInvestment(
                502L, BigDecimal.valueOf(60000.00),
                Investment.InvestmentStatus.ACTIVE,
                LocalDateTime.of(2026, 3, 5, 14, 0),
                secondLand);

        given(userRepository.findById(10L)).willReturn(Optional.of(investor));
        given(investmentRepository.findAllByUserIdWithLand(10L)).willReturn(List.of(inv1, inv2));
        given(investmentRepository.sumTotalByUserId(10L))
                .willReturn(BigDecimal.valueOf(160000.00));
        given(investmentRepository.sumByUserIdAndStatus(10L, Investment.InvestmentStatus.ACTIVE))
                .willReturn(BigDecimal.valueOf(160000.00));

        Map<String, Object> result = investorDashboardService.getPortfolio(10L);

        assertEquals(BigDecimal.valueOf(160000.00), result.get("totalInvested"));
        assertEquals(2,                             result.get("count"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> investments =
                (List<Map<String, Object>>) result.get("investments");

        assertEquals("Pepper Estate Phase 1", investments.get(0).get("projectName"));
        assertEquals("Tea Revival Project",   investments.get(1).get("projectName"));
    }

    // ── AC-8: getDashboard() throws when investor not found ─────────────────────────────
    @Test
    void getDashboard_whenInvestorNotFound_shouldThrowResourceNotFoundException() {
        given(userRepository.findById(10L)).willReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> investorDashboardService.getDashboard(10L));

        assertEquals("Investor not found: 10", ex.getMessage());
    }

    // ── AC-6: getDashboard() throws when wallet not found ────────────────────────────────
    @Test
    void getDashboard_whenWalletNotFound_shouldThrowResourceNotFoundException() {
        given(userRepository.findById(10L)).willReturn(Optional.of(investor));
        given(walletRepository.findByUserUserId(10L)).willReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> investorDashboardService.getDashboard(10L));

        assertEquals("Wallet not found for investor: 10", ex.getMessage());
    }

    // ── AC-8: getReports() summarises investment activity accurately ────────────────────
    @Test
    void getReports_shouldReturnAccurateFinancialSummary() {
        given(userRepository.findById(10L)).willReturn(Optional.of(investor));
        given(walletRepository.findByUserUserId(10L)).willReturn(Optional.of(wallet));
        given(investmentRepository.sumTotalByUserId(10L))
                .willReturn(BigDecimal.valueOf(100000.00));
        given(investmentRepository.sumByUserIdAndStatus(10L, Investment.InvestmentStatus.ACTIVE))
                .willReturn(BigDecimal.valueOf(100000.00));
        given(investmentRepository.sumByUserIdAndStatus(10L, Investment.InvestmentStatus.COMPLETED))
                .willReturn(BigDecimal.ZERO);
        given(investmentRepository.sumByUserIdAndStatus(10L, Investment.InvestmentStatus.PENDING))
                .willReturn(BigDecimal.ZERO);
        given(investmentRepository.countDistinctLandsByUserId(10L)).willReturn(1L);
        given(investmentRepository.countActiveLandsByUserId(10L)).willReturn(1L);

        Map<String, Object> result  = investorDashboardService.getReports(10L);

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) result.get("summary");

        assertEquals(BigDecimal.valueOf(100000.00), summary.get("totalInvested"));
        assertEquals(BigDecimal.valueOf(100000.00), summary.get("activeInvestments"));
        assertEquals(BigDecimal.ZERO,               summary.get("completedReturns"));
        assertEquals(BigDecimal.ZERO,               summary.get("pendingInvestments"));
        assertEquals(BigDecimal.valueOf(400000.00), summary.get("walletBalance"));
        assertEquals("LKR",                         summary.get("currency"));
        assertEquals(1L,                            summary.get("totalLands"));
        assertEquals(1L,                            summary.get("activeLands"));
    }

    // ── Helper ────────────────────────────────────────────────────────────────────────────
    private Investment buildInvestment(Long id, BigDecimal amount,
                                       Investment.InvestmentStatus status, LocalDateTime date, Land land) {
        Investment inv = new Investment();
        inv.setInvestmentId(id);
        inv.setAmountInvested(amount);
        inv.setStatus(status);
        inv.setInvestmentDate(date);
        inv.setInvestor(investor);
        inv.setLand(land);
        return inv;
    }
}